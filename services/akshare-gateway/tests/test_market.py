from datetime import datetime

import pandas as pd
import pytest

from akshare_gateway.market import (
    InvalidMarketData,
    build_market_snapshot,
    build_single_quote,
    market_phase,
)


def test_builds_traceable_quotes_for_shenzhen_shanghai_and_beijing(
    market_frame: pd.DataFrame, fetched_at: datetime
) -> None:
    snapshot = build_market_snapshot(market_frame, fetched_at)

    sh = snapshot["SSE:600519"]
    assert sh["instrumentId"] == "SSE:600519"
    assert sh["name"] == "贵州茅台"
    assert sh["last"] == 1450.5
    assert sh["previousClose"] == 1440.0
    assert sh["change"] == 10.5
    assert sh["source"] == "AKSHARE"
    assert sh["sourceTimestamp"] == fetched_at
    assert sh["delayed"] is True
    assert sh["stale"] is False
    assert sh["demo"] is False
    assert snapshot["SZSE:000001"]["instrumentId"] == "SZSE:000001"
    assert snapshot["BSE:920001"]["instrumentId"] == "BSE:920001"

    eastmoney = build_market_snapshot(
        market_frame, fetched_at, "AKSHARE_EASTMONEY_SNAPSHOT"
    )
    assert eastmoney["SSE:600519"]["volume"] == sh["volume"] * 100


def test_accepts_prefixed_codes_from_sina(
    market_frame: pd.DataFrame, fetched_at: datetime
) -> None:
    market_frame.loc[0, "代码"] = "sh600519"
    market_frame.loc[1, "代码"] = "sz000001"
    market_frame.loc[2, "代码"] = "bj920001"

    snapshot = build_market_snapshot(market_frame, fetched_at)

    assert set(snapshot) == {"SSE:600519", "SZSE:000001", "BSE:920001"}


def test_rejects_snapshot_without_required_columns(fetched_at: datetime) -> None:
    with pytest.raises(InvalidMarketData, match="缺少字段"):
        build_market_snapshot(pd.DataFrame([{"代码": "600519"}]), fetched_at)


def test_skips_rows_with_non_positive_or_missing_price(
    market_frame: pd.DataFrame, fetched_at: datetime
) -> None:
    market_frame.loc[0, "最新价"] = float("nan")
    market_frame.loc[1, "最新价"] = 0

    snapshot = build_market_snapshot(market_frame, fetched_at)

    assert set(snapshot) == {"BSE:920001"}


def test_builds_eastmoney_single_quote(fetched_at: datetime) -> None:
    frame = pd.DataFrame(
        [
            {"item": "最新", "value": 10.45},
            {"item": "昨收", "value": 10.40},
            {"item": "今开", "value": 10.38},
            {"item": "最高", "value": 10.47},
            {"item": "最低", "value": 10.37},
            {"item": "涨跌", "value": 0.05},
            {"item": "涨幅", "value": 0.48},
            {"item": "总手", "value": 872663},
        ]
    )

    quote = build_single_quote(
        frame, "SZSE:000001", fetched_at, "AKSHARE_EASTMONEY_SINGLE"
    )

    assert quote["last"] == 10.45
    assert quote["name"] == "000001"
    assert quote["source"] == "AKSHARE_EASTMONEY_SINGLE"
    assert quote["volume"] == 87266300
    assert quote["demo"] is False


def test_builds_xueqiu_single_quote(fetched_at: datetime) -> None:
    frame = pd.DataFrame(
        [
            {"item": "名称", "value": "浦发银行"},
            {"item": "现价", "value": 10.2},
            {"item": "昨收", "value": 9.65},
            {"item": "今开", "value": 9.8},
            {"item": "最高", "value": 10.29},
            {"item": "最低", "value": 9.75},
            {"item": "涨跌", "value": 0.55},
            {"item": "涨幅", "value": 5.7},
            {"item": "成交量", "value": 149422915},
        ]
    )

    quote = build_single_quote(frame, "SSE:600000", fetched_at, "AKSHARE_XUEQIU_SINGLE")

    assert quote["name"] == "浦发银行"
    assert quote["changePercent"] == 5.7


def test_rejects_single_quote_without_required_item_value(fetched_at: datetime) -> None:
    with pytest.raises(InvalidMarketData, match="item/value"):
        build_single_quote(
            pd.DataFrame([{"名称": "错误"}]),
            "SSE:600000",
            fetched_at,
            "AKSHARE_XUEQIU_SINGLE",
        )


@pytest.mark.parametrize(
    ("instant", "expected"),
    [
        ("2026-07-20T01:10:00+00:00", "PRE_OPEN"),
        ("2026-07-20T01:20:00+00:00", "AUCTION"),
        ("2026-07-20T02:00:00+00:00", "CONTINUOUS"),
        ("2026-07-20T04:00:00+00:00", "BREAK"),
        ("2026-07-20T06:00:00+00:00", "CONTINUOUS"),
        ("2026-07-20T07:10:00+00:00", "CLOSED"),
        ("2026-07-19T02:00:00+00:00", "CLOSED"),
    ],
)
def test_market_phase_uses_asia_shanghai_trading_hours(
    instant: str, expected: str
) -> None:
    assert market_phase(datetime.fromisoformat(instant)) == expected
