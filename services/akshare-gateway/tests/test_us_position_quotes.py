from datetime import datetime

import pytest

from akshare_gateway.us_position_quotes import (
    fetch_us_position_quotes,
    validate_us_instruments,
)


class FakeResponse:
    def __init__(self, body: str) -> None:
        self.content = body.encode("gb18030")

    def raise_for_status(self) -> None:
        return None


def payload(symbol: str, name: str = "Apple Inc.") -> str:
    fields = [""] * 36
    fields[0] = name
    fields[1] = "220.03"
    fields[2] = "1.25"
    fields[3] = "2026-08-07 16:00:00"
    fields[4] = "2.71"
    fields[5] = "218.10"
    fields[6] = "221.00"
    fields[7] = "217.20"
    fields[10] = "123456"
    fields[26] = "217.32"
    return f'var hq_str_gb_{symbol}="{",".join(fields)}";'


def test_fetches_requested_symbols_in_batches_and_ignores_unrequested_rows() -> None:
    calls = []

    def fake_get(url, **kwargs):
        calls.append((url, kwargs))
        return FakeResponse("\n".join([
            payload("aapl"), payload("msft", "Microsoft"), payload("tsla", "Tesla")
        ]))

    result = fetch_us_position_quotes(
        ["NASDAQ:AAPL", "NASDAQ:AAPL", "NYSE:MSFT"],
        batch_size=1,
        http_get=fake_get,
    )

    assert [item["instrumentId"] for item in result] == ["NASDAQ:AAPL", "NYSE:MSFT"]
    assert result[0]["source"] == "AKSHARE_SINA_POSITION"
    assert result[0]["last"] == 220.03
    assert result[0]["sourceTimestamp"] == datetime.fromisoformat(
        "2026-08-07T08:00:00+00:00")
    assert len(calls) == 2
    assert all(call[1]["headers"]["Referer"].startswith("https://finance.sina.com.cn")
               for call in calls)


@pytest.mark.parametrize("instrument", [
    "AAPL", "NASDAQ:AAPL;DROP", "SSE:600000", "NASDAQ:", "NASDAQ:中文",
])
def test_rejects_invalid_identifiers_before_http(instrument: str) -> None:
    calls = []
    with pytest.raises(ValueError, match="格式无效"):
        fetch_us_position_quotes([instrument], http_get=lambda *args, **kwargs: calls.append(args))
    assert calls == []


def test_validation_normalizes_and_deduplicates() -> None:
    assert validate_us_instruments([" nasdaq:aapl ", "NASDAQ:AAPL", "AMEX:SPY"]) == [
        "NASDAQ:AAPL", "AMEX:SPY"
    ]
