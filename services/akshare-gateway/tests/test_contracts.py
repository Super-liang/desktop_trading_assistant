from __future__ import annotations

import pandas as pd
import pytest

from akshare_gateway.contracts import (
    CatalogMarket,
    ContractViolation,
    INDEX_DEFINITIONS,
    normalize_catalog,
    normalize_index_frames,
    validate_frame_contract,
    validate_columns,
)


def test_index_definitions_are_stable_and_ordered() -> None:
    assert [item.instrument_id for item in INDEX_DEFINITIONS] == [
        "SSE_INDEX:000001",
        "SZSE_INDEX:399001",
        "SZSE_INDEX:399006",
        "BSE_INDEX:899050",
        "SSE_INDEX:000680",
        "SSE_INDEX:000688",
        "CSI_INDEX:000300",
    ]


def test_contract_rejects_missing_columns() -> None:
    with pytest.raises(ContractViolation, match="缺少列"):
        validate_columns(pd.DataFrame({"代码": ["000001"]}), {"代码", "名称", "最新价"}, "指数")


@pytest.mark.parametrize(
    ("contract", "columns"),
    [
        ("A_SHARE_EASTMONEY", ["代码", "名称", "最新价", "涨跌额", "涨跌幅", "今开", "昨收"]),
        ("HK_STOCK_EASTMONEY", ["代码", "名称", "最新价", "涨跌额", "涨跌幅", "今开", "昨收"]),
        ("HK_STOCK_SINA", ["代码", "中文名称", "最新价", "涨跌额", "涨跌幅", "今开", "昨收"]),
        ("US_STOCK_EASTMONEY", ["代码", "名称", "最新价", "涨跌额", "涨跌幅", "开盘价", "昨收价"]),
        ("OPEN_FUND_CATALOG", ["基金代码", "基金简称", "基金类型"]),
        ("OPEN_FUND_NAV", ["基金代码", "单位净值", "净值日期"]),
        ("A_SHARE_INDEX", ["代码", "名称", "最新价", "涨跌额", "涨跌幅", "今开", "昨收"]),
    ],
)
def test_akshare_frame_contracts_accept_documented_columns(contract: str, columns: list[str]) -> None:
    validate_frame_contract(pd.DataFrame(columns=columns), contract)


@pytest.mark.parametrize("contract", [
    "A_SHARE_EASTMONEY", "A_SHARE_SINA", "HK_STOCK_EASTMONEY", "HK_STOCK_SINA",
    "US_STOCK_EASTMONEY", "US_STOCK_SINA", "OPEN_FUND_CATALOG",
    "OPEN_FUND_NAV", "A_SHARE_INDEX",
])
def test_every_contract_fails_closed_when_the_provider_schema_drifts(contract: str) -> None:
    with pytest.raises(ContractViolation):
        validate_frame_contract(pd.DataFrame({"unexpected": []}), contract)


def test_index_frames_merge_by_market_qualified_identity() -> None:
    frame = pd.DataFrame(
        [
            {"代码": "000001", "名称": "上证指数", "最新价": 3100, "涨跌额": 1, "涨跌幅": 0.03, "今开": 3098, "昨收": 3099},
            {"代码": "000680", "名称": "科创综指", "最新价": 1200, "涨跌额": -2, "涨跌幅": -0.17, "今开": 1205, "昨收": 1202},
        ]
    )

    result = normalize_index_frames([frame], "EASTMONEY", "2026-07-29T01:30:00Z")

    assert result["SSE_INDEX:000001"]["name"] == "上证指数"
    assert result["SSE_INDEX:000680"]["price"] == 1200.0
    assert "SSE_INDEX:000688" not in result


def test_index_normalization_does_not_accept_approximate_name() -> None:
    frame = pd.DataFrame(
        [{"代码": "123456", "名称": "类似上证指数", "最新价": 1, "涨跌额": 0, "涨跌幅": 0, "今开": 1, "昨收": 1}]
    )
    assert normalize_index_frames([frame], "EASTMONEY", "2026-07-29T01:30:00Z") == {}


def test_normalizes_hk_catalog_with_five_digit_code() -> None:
    result = normalize_catalog(
        pd.DataFrame([{"代码": "700", "名称": "腾讯控股"}]),
        CatalogMarket.HK_STOCK,
    )

    assert result["HKEX:00700"] == {
        "code": "00700", "name": "腾讯控股", "market": "HK_STOCK",
        "exchange": "HKEX", "currency": "HKD", "assetType": "STOCK",
        "providerSymbol": "700",
    }


@pytest.mark.parametrize(("provider_symbol", "instrument_id"), [
    ("105.AAPL", "NASDAQ:AAPL"),
    ("106.BRK.B", "NYSE:BRK.B"),
    ("107.SPY", "AMEX:SPY"),
])
def test_maps_eastmoney_us_market_number(provider_symbol: str, instrument_id: str) -> None:
    result = normalize_catalog(
        pd.DataFrame([{"代码": provider_symbol, "名称": "Example"}]),
        CatalogMarket.US_STOCK,
    )

    assert result[instrument_id]["providerSymbol"] == provider_symbol
    assert result[instrument_id]["currency"] == "USD"


def test_rejects_unknown_us_market_number() -> None:
    result = normalize_catalog(
        pd.DataFrame([{"代码": "999.AAPL", "名称": "Apple"}]),
        CatalogMarket.US_STOCK,
    )
    assert result == {}


def test_fund_catalog_excludes_money_and_closed_funds() -> None:
    frame = pd.DataFrame([
        {"基金代码": "000001", "基金简称": "开放基金", "基金类型": "混合型-偏股"},
        {"基金代码": "000002", "基金简称": "货币基金", "基金类型": "货币型-普通货币"},
        {"基金代码": "000003", "基金简称": "封闭基金", "基金类型": "封闭式"},
    ])

    result = normalize_catalog(frame, CatalogMarket.PUBLIC_FUND)

    assert list(result) == ["CN_FUND:000001"]
    assert result["CN_FUND:000001"]["assetType"] == "OPEN_END_FUND"
