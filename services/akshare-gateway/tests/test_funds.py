from datetime import datetime, timezone

import pandas as pd

from akshare_gateway.funds import normalize_current_unit_nav, normalize_fund_history


NOW = datetime(2026, 7, 29, 23, 0, tzinfo=timezone.utc)


def test_current_open_fund_nav_uses_unit_nav_not_accumulated_nav() -> None:
    frame = pd.DataFrame([{
        "基金代码": "1", "基金简称": "开放基金",
        "2026-07-29-单位净值": "1.25", "2026-07-29-累计净值": "5.50",
        "2026-07-28-单位净值": "1.20", "2026-07-28-累计净值": "5.45",
    }])

    result = normalize_current_unit_nav(frame, NOW)["CN_FUND:000001"]

    assert result["unitNav"] == 1.25
    assert result["previousUnitNav"] == 1.20
    assert result["navDate"] == "2026-07-29"


def test_history_fallback_selects_latest_two_distinct_nav_dates() -> None:
    frame = pd.DataFrame([
        {"净值日期": "2026-07-28", "单位净值": "1.2"},
        {"净值日期": "2026-07-29", "单位净值": "1.25"},
    ])

    result = normalize_fund_history(frame, "000001", "开放基金", NOW)

    assert result is not None
    assert result["unitNav"] == 1.25
    assert result["previousNavDate"] == "2026-07-28"
