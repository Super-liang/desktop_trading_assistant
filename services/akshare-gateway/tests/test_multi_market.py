from datetime import datetime, timezone

import pandas as pd

from akshare_gateway.contracts import CatalogMarket
from akshare_gateway.multi_market import build_cross_market_snapshot


NOW = datetime(2026, 7, 29, 2, 0, tzinfo=timezone.utc)


def test_normalizes_hk_eastmoney_snapshot_as_hkd_identity() -> None:
    frame = pd.DataFrame([{
        "代码": "700", "名称": "腾讯控股", "最新价": 500, "涨跌额": 5,
        "涨跌幅": 1, "今开": 496, "昨收": 495, "最高": 501,
        "最低": 494, "成交量": 1000,
    }])

    result = build_cross_market_snapshot(
        frame, CatalogMarket.HK_STOCK, "EASTMONEY", NOW)

    assert result["HKEX:00700"]["last"] == 500
    assert result["HKEX:00700"]["delayed"] is True


def test_normalizes_hk_sina_snapshot_as_hkd_identity() -> None:
    frame = pd.DataFrame([{
        "代码": "700", "中文名称": "腾讯控股", "最新价": 500, "涨跌额": 5,
        "涨跌幅": 1, "今开": 496, "昨收": 495, "最高": 501,
        "最低": 494, "成交量": 1000,
    }])

    result = build_cross_market_snapshot(
        frame, CatalogMarket.HK_STOCK, "SINA", NOW)

    assert result["HKEX:00700"]["name"] == "腾讯控股"
    assert result["HKEX:00700"]["source"] == "AKSHARE_SINA_SNAPSHOT"


def test_normalizes_us_eastmoney_exchange_and_prices() -> None:
    frame = pd.DataFrame([{
        "代码": "105.AAPL", "名称": "Apple", "最新价": 200, "涨跌额": 2,
        "涨跌幅": 1, "开盘价": 199, "昨收价": 198, "最高价": 201,
        "最低价": 197, "成交量": 100,
    }])

    result = build_cross_market_snapshot(
        frame, CatalogMarket.US_STOCK, "EASTMONEY", NOW)

    assert result["NASDAQ:AAPL"]["previousClose"] == 198
    assert result["NASDAQ:AAPL"]["source"] == "AKSHARE_EASTMONEY_SNAPSHOT"


def test_normalizes_us_sina_delayed_fallback() -> None:
    frame = pd.DataFrame([{
        "name": "Apple Inc", "cname": "苹果", "symbol": "AAPL",
        "price": "200", "diff": "2", "chg": "1", "preclose": "198",
        "open": "199", "high": "201", "low": "197", "volume": "100",
        "market": "NASDAQ",
    }])

    result = build_cross_market_snapshot(frame, CatalogMarket.US_STOCK, "SINA", NOW)

    assert result["NASDAQ:AAPL"]["name"] == "苹果"
    assert result["NASDAQ:AAPL"]["delayed"] is True
