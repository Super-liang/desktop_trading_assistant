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
    frame: pd.DataFrame, fetched_at: datetime
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
            code = str(row["代码"]).strip().lower()
            if code.startswith(("sh", "sz", "bj")):
                code = code[2:]
            code = code.zfill(6)
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
            "source": "AKSHARE",
            "sourceTimestamp": fetched_at,
            "receivedAt": fetched_at,
            "delayed": True,
            "stale": False,
            "demo": False,
        }
    if not quotes:
        raise InvalidMarketData("AKShare 行情没有可用证券")
    return quotes
