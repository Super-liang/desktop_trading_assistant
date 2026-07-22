from __future__ import annotations

from datetime import datetime, time
from math import isfinite
from typing import Any
from zoneinfo import ZoneInfo

import pandas as pd

CHINA = ZoneInfo("Asia/Shanghai")
REQUIRED_COLUMNS = {
    "代码",
    "名称",
    "最新价",
    "昨收",
    "今开",
    "最高",
    "最低",
    "涨跌额",
    "涨跌幅",
    "成交量",
}


class InvalidMarketData(RuntimeError):
    """AKShare 返回的表结构或内容无法安全转换。"""


def exchange_for(code: str) -> str:
    if len(code) != 6 or not code.isdigit():
        raise InvalidMarketData("证券代码必须为六位数字")
    if code[0] in {"5", "6"}:
        return "SSE"
    if code[0] in {"0", "1", "2", "3"}:
        return "SZSE"
    if code[0] in {"4", "8", "9"}:
        return "BSE"
    raise InvalidMarketData("不支持的 A 股代码")


def normalize_code(value: Any) -> str:
    """统一 AKShare 在不同 DataFrame 类型中返回的证券代码。"""
    code = str(value).strip().lower()
    if code.startswith(("sh", "sz", "bj")):
        code = code[2:]
    if code.endswith(".0") and code[:-2].isdigit():
        code = code[:-2]
    code = code.zfill(6)
    exchange_for(code)
    return code


def asset_type_for(code: str) -> str:
    return "ETF" if code.startswith(("1", "5")) else "STOCK"


def market_phase(instant: datetime) -> str:
    local = instant.astimezone(CHINA)
    if local.weekday() >= 5:
        return "CLOSED"
    current = local.time().replace(tzinfo=None)
    if current < time(9, 15):
        return "PRE_OPEN"
    if current < time(9, 30):
        return "AUCTION"
    if current < time(11, 30):
        return "CONTINUOUS"
    if current < time(13, 0):
        return "BREAK"
    if current < time(15, 0):
        return "CONTINUOUS"
    return "CLOSED"


def _number(value: Any) -> float:
    try:
        result = float(value)
    except (TypeError, ValueError) as exception:
        raise InvalidMarketData("行情数值无效") from exception
    if not isfinite(result):
        raise InvalidMarketData("行情数值无效")
    return result


def build_market_snapshot(
    frame: pd.DataFrame, fetched_at: datetime, source: str = "AKSHARE"
) -> dict[str, dict[str, Any]]:
    missing = REQUIRED_COLUMNS.difference(frame.columns)
    if missing:
        raise InvalidMarketData(f"AKShare 行情缺少字段: {','.join(sorted(missing))}")
    if fetched_at.tzinfo is None:
        raise ValueError("fetched_at 必须包含时区")

    phase = market_phase(fetched_at)
    quotes: dict[str, dict[str, Any]] = {}
    for row in frame.to_dict(orient="records"):
        try:
            code = normalize_code(row["代码"])
            exchange = exchange_for(code)
            last = _number(row["最新价"])
            if last <= 0:
                continue
            previous_close = _number(row["昨收"])
            open_price = _number(row["今开"])
            high = _number(row["最高"])
            low = _number(row["最低"])
            change = _number(row["涨跌额"])
            change_percent = _number(row["涨跌幅"])
            volume = _number(row["成交量"])
            if "EASTMONEY" in source:
                # stock_zh_a_spot_em 的成交量单位为“手”，统一 Quote 使用“股”。
                volume *= 100
            if previous_close <= 0 or min(open_price, high, low, volume) < 0:
                continue
        except InvalidMarketData:
            # 停牌、缺失或非 A 股行不生成虚假价格。
            continue

        instrument_id = f"{exchange}:{code}"
        quotes[instrument_id] = {
            "instrumentId": instrument_id,
            "name": str(row["名称"]).strip() or code,
            "last": last,
            "previousClose": previous_close,
            "open": open_price,
            "high": high,
            "low": low,
            "change": change,
            "changePercent": change_percent,
            "volume": volume,
            "marketPhase": phase,
            "source": source,
            "sourceTimestamp": fetched_at,
            "receivedAt": fetched_at,
            "delayed": True,
            "stale": False,
            "demo": False,
        }
    if not quotes:
        raise InvalidMarketData("AKShare 行情没有可用证券")
    return quotes


def build_single_quote(
    frame: pd.DataFrame, instrument_id: str, fetched_at: datetime, source: str
) -> dict[str, Any]:
    """把 AKShare 的 item/value 单股结果转换为统一 Quote。"""
    if fetched_at.tzinfo is None:
        raise ValueError("fetched_at 必须包含时区")
    if not {"item", "value"}.issubset(frame.columns):
        raise InvalidMarketData("AKShare 单股行情缺少 item/value 字段")
    exchange, code = instrument_id.split(":", 1)
    if exchange_for(code) != exchange:
        raise InvalidMarketData("证券交易所与代码不匹配")
    values = {
        str(row["item"]).strip(): row["value"]
        for row in frame.to_dict(orient="records")
    }

    def first_number(*names: str) -> float:
        for name in names:
            if name in values and values[name] not in (None, "", "-"):
                return _number(values[name])
        raise InvalidMarketData(f"AKShare 单股行情缺少字段: {'/'.join(names)}")

    last = first_number("最新", "现价", "最新价", "current")
    previous_close = first_number("昨收", "昨收价", "last_close")
    if last <= 0 or previous_close <= 0:
        raise InvalidMarketData("AKShare 单股行情价格无效")
    change = first_number("涨跌", "涨跌额", "chg") if any(
        name in values for name in ("涨跌", "涨跌额", "chg")
    ) else last - previous_close
    change_percent = first_number("涨幅", "涨跌幅", "percent") if any(
        name in values for name in ("涨幅", "涨跌幅", "percent")
    ) else change * 100 / previous_close
    name = str(values.get("名称") or values.get("name") or code).strip()
    return {
        "instrumentId": instrument_id,
        "name": name,
        "last": last,
        "previousClose": previous_close,
        "open": first_number("今开", "开盘", "open"),
        "high": first_number("最高", "high"),
        "low": first_number("最低", "low"),
        "change": change,
        "changePercent": change_percent,
        "volume": first_number("成交量", "总手", "volume")
        * (100 if "EASTMONEY" in source and "总手" in values else 1),
        "marketPhase": market_phase(fetched_at),
        "source": source,
        "sourceTimestamp": fetched_at,
        "receivedAt": fetched_at,
        "delayed": True,
        "stale": False,
        "demo": False,
    }
