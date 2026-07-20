from datetime import datetime, timezone

import pandas as pd
import pytest


@pytest.fixture
def fetched_at() -> datetime:
    return datetime(2026, 7, 20, 9, 31, tzinfo=timezone.utc)


@pytest.fixture
def market_frame() -> pd.DataFrame:
    return pd.DataFrame(
        [
            {
                "代码": "600519",
                "名称": "贵州茅台",
                "最新价": 1450.5,
                "昨收": 1440.0,
                "今开": 1441.0,
                "最高": 1460.0,
                "最低": 1430.0,
                "涨跌额": 10.5,
                "涨跌幅": 0.7292,
                "成交量": 123456,
            },
            {
                "代码": "000001",
                "名称": "平安银行",
                "最新价": 11.2,
                "昨收": 11.0,
                "今开": 11.05,
                "最高": 11.3,
                "最低": 10.98,
                "涨跌额": 0.2,
                "涨跌幅": 1.8182,
                "成交量": 654321,
            },
            {
                "代码": "920001",
                "名称": "北交测试",
                "最新价": 18.6,
                "昨收": 18.0,
                "今开": 18.1,
                "最高": 18.8,
                "最低": 17.9,
                "涨跌额": 0.6,
                "涨跌幅": 3.3333,
                "成交量": 10000,
            },
        ]
    )
