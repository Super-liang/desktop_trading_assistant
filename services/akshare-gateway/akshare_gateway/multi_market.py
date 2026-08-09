from __future__ import annotations

from datetime import datetime
from math import isfinite
from typing import Any

import pandas as pd

from .contracts import (
    CatalogMarket,
    US_EASTMONEY_EXCHANGES,
    validate_frame_contract,
)
from .market import InvalidMarketData


def build_cross_market_snapshot(
    frame: pd.DataFrame,
    market: CatalogMarket,
    source: str,
    fetched_at: datetime,
) -> dict[str, dict[str, Any]]:
    if fetched_at.tzinfo is None:
        raise ValueError("fetched_at 必须包含时区")
    if market == CatalogMarket.HK_STOCK and source in {"EASTMONEY", "SINA"}:
        validate_frame_contract(frame, f"HK_STOCK_{source}")
        rows = (_hk_quote(row, source, fetched_at) for row in frame.to_dict(orient="records"))
    elif market == CatalogMarket.US_STOCK and source == "EASTMONEY":
        validate_frame_contract(frame, "US_STOCK_EASTMONEY")
        rows = (_us_eastmoney_quote(row, source, fetched_at)
                for row in frame.to_dict(orient="records"))
    elif market == CatalogMarket.US_STOCK and source == "SINA":
        validate_frame_contract(frame, "US_STOCK_SINA")
        rows = (_us_sina_quote(row, source, fetched_at)
                for row in frame.to_dict(orient="records"))
    else:
        raise InvalidMarketData("所选市场不支持该行情来源")
    result: dict[str, dict[str, Any]] = {}
    for quote in rows:
        if quote is not None:
            result[quote["instrumentId"]] = quote
    if not result:
        raise InvalidMarketData("AKShare 行情没有可用证券")
    return result


def _hk_quote(row: dict, source: str, fetched_at: datetime) -> dict | None:
    raw_code = str(row["代码"]).strip()
    code = raw_code.zfill(5)
    if not code.isdigit() or len(code) != 5:
        return None
    return _quote(
        f"HKEX:{code}", row.get("中文名称") or row.get("名称") or code,
        row["最新价"], row["昨收"], row["今开"],
        row.get("最高"), row.get("最低"), row["涨跌额"], row["涨跌幅"],
        row.get("成交量"), source, fetched_at,
    )


def _us_eastmoney_quote(row: dict, source: str, fetched_at: datetime) -> dict | None:
    identity = eastmoney_us_identity(row["代码"])
    if identity is None:
        return None
    return _quote(
        identity, row["名称"], row["最新价"], row["昨收价"], row["开盘价"],
        row.get("最高价"), row.get("最低价"), row["涨跌额"], row["涨跌幅"],
        row.get("成交量"), source, fetched_at,
    )


def _us_sina_quote(row: dict, source: str, fetched_at: datetime) -> dict | None:
    code = str(row["symbol"]).strip().upper()
    exchange = _sina_us_exchange(str(row.get("market", "")))
    if exchange is None or not code:
        return None
    return _quote(
        f"{exchange}:{code}", row.get("cname") or row.get("name") or code,
        row["price"], row["preclose"], row["open"], row["high"], row["low"],
        row["diff"], row["chg"], row["volume"], source, fetched_at,
    )


def eastmoney_us_identity(value: object) -> str | None:
    provider_symbol = str(value).strip().upper()
    if "." not in provider_symbol:
        return None
    market_code, code = provider_symbol.split(".", 1)
    exchange = US_EASTMONEY_EXCHANGES.get(market_code)
    if exchange is None or not code:
        return None
    return f"{exchange}:{code}"


def _sina_us_exchange(value: str) -> str | None:
    normalized = value.strip().upper()
    if normalized in {"NASDAQ", "NAS"}:
        return "NASDAQ"
    if normalized in {"NYSE", "NYS"}:
        return "NYSE"
    if normalized in {"AMEX", "ASE"}:
        return "AMEX"
    return None


def _quote(instrument_id: str, name: object, last: object, previous_close: object,
           open_price: object, high: object, low: object, change: object,
           change_percent: object, volume: object, source: str,
           fetched_at: datetime) -> dict | None:
    try:
        last_value = _number(last)
        previous_value = _number(previous_close)
        if last_value <= 0 or previous_value <= 0:
            return None
        values = [_number(value, default=0) for value in
                  (open_price, high, low, change, change_percent, volume)]
    except (TypeError, ValueError):
        return None
    return {
        "instrumentId": instrument_id,
        "name": str(name).strip() or instrument_id.split(":", 1)[1],
        "last": last_value,
        "previousClose": previous_value,
        "open": values[0], "high": values[1], "low": values[2],
        "change": values[3], "changePercent": values[4], "volume": values[5],
        "marketPhase": "UNKNOWN",
        "source": f"AKSHARE_{source}_SNAPSHOT",
        "sourceTimestamp": fetched_at,
        "receivedAt": fetched_at,
        "delayed": True,
        "stale": False,
        "demo": False,
    }


def _number(value: object, default: float | None = None) -> float:
    if value is None or pd.isna(value) or value in ("", "-"):
        if default is not None:
            return default
        raise ValueError("行情数值缺失")
    number = float(value)
    if not isfinite(number):
        raise ValueError("行情数值无效")
    return number
