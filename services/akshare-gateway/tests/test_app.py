from datetime import datetime
from time import monotonic, sleep

import pandas as pd
import pytest
from fastapi.testclient import TestClient

from akshare_gateway import app as app_module
from akshare_gateway.app import Settings, create_app, load_akshare_frame, load_market_frame
from akshare_gateway.sources import MarketSource, SingleSource
from akshare_gateway import http_timeout
from akshare_gateway.isolation import run_isolated


def slow_isolated_worker(sender, *args) -> None:
    sleep(10)


def client_for(
    market_frame: pd.DataFrame, fetched_at: datetime, **loaders
) -> TestClient:
    settings = Settings(
        api_key="test-shared-key",
        cache_ttl_seconds=3,
        max_stale_seconds=30,
        search_limit=20,
    )
    return TestClient(
        create_app(
            settings=settings, frame_loader=lambda: market_frame,
            utcnow=lambda: fetched_at, **loaders,
        )
    )


def test_akshare_loader_falls_back_to_sina(monkeypatch, market_frame: pd.DataFrame) -> None:
    import akshare as ak

    def unavailable():
        raise ConnectionError("eastmoney unavailable")

    monkeypatch.setattr(ak, "stock_zh_a_spot_em", unavailable)
    monkeypatch.setattr(ak, "stock_zh_a_spot", lambda: market_frame)

    assert load_akshare_frame() is market_frame


def test_health_is_public_and_does_not_expose_api_key(
    market_frame: pd.DataFrame, fetched_at: datetime
) -> None:
    client = client_for(market_frame, fetched_at)

    response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "UP"
    assert "test-shared-key" not in response.text


def test_v1_endpoints_require_matching_api_key(
    market_frame: pd.DataFrame, fetched_at: datetime
) -> None:
    client = client_for(market_frame, fetched_at)

    assert client.get("/v1/search", params={"query": "茅台"}).status_code == 401
    response = client.get(
        "/v1/search",
        params={"query": "茅台"},
        headers={"X-API-Key": "wrong"},
    )
    assert response.status_code == 401
    assert "test-shared-key" not in response.text


def test_searches_by_code_or_name(
    market_frame: pd.DataFrame, fetched_at: datetime
) -> None:
    client = client_for(market_frame, fetched_at)
    headers = {"X-API-Key": "test-shared-key"}

    by_code = client.get("/v1/search", params={"query": "600519"}, headers=headers)
    by_name = client.get("/v1/search", params={"query": "平安"}, headers=headers)

    assert by_code.status_code == 200
    assert by_code.json() == [
        {
            "instrumentId": "SSE:600519",
            "code": "600519",
            "name": "贵州茅台",
            "exchange": "SSE",
            "assetType": "STOCK",
        }
    ]
    assert by_name.json()[0]["instrumentId"] == "SZSE:000001"


def test_returns_quotes_in_requested_order(
    market_frame: pd.DataFrame, fetched_at: datetime
) -> None:
    client = client_for(market_frame, fetched_at)

    response = client.get(
        "/v1/snapshots",
        params={"symbols": "SZSE:000001,SSE:600519"},
        headers={"X-API-Key": "test-shared-key"},
    )

    assert response.status_code == 200
    assert [quote["instrumentId"] for quote in response.json()] == [
        "SZSE:000001",
        "SSE:600519",
    ]
    assert all(quote["source"] == "AKSHARE" for quote in response.json())


def test_rejects_invalid_or_missing_symbols(
    market_frame: pd.DataFrame, fetched_at: datetime
) -> None:
    client = client_for(market_frame, fetched_at)
    headers = {"X-API-Key": "test-shared-key"}

    invalid = client.get("/v1/snapshots", params={"symbols": "600519"}, headers=headers)
    missing = client.get(
        "/v1/snapshots", params={"symbols": "SSE:600000"}, headers=headers
    )

    assert invalid.status_code == 422
    assert missing.status_code == 404


def test_explicit_market_source_does_not_fallback(monkeypatch, market_frame: pd.DataFrame) -> None:
    import akshare as ak

    calls = []
    monkeypatch.setattr(ak, "stock_zh_a_spot_em", lambda: calls.append("eastmoney") or market_frame)
    monkeypatch.setattr(ak, "stock_zh_a_spot", lambda: calls.append("sina") or market_frame)

    assert load_market_frame(MarketSource.SINA) is market_frame
    assert calls == ["sina"]


def test_eastmoney_single_rejects_bse_before_calling_akshare(monkeypatch) -> None:
    import akshare as ak

    calls = []
    monkeypatch.setattr(ak, "stock_bid_ask_em", lambda symbol: calls.append(symbol))

    try:
        app_module.load_single_frame(SingleSource.EASTMONEY, "BSE:830799")
        raise AssertionError("应拒绝北交所代码")
    except ValueError as exception:
        assert "北交所" in str(exception)
    assert calls == []


def test_market_snapshot_uses_requested_source(
    monkeypatch, market_frame: pd.DataFrame, fetched_at: datetime
) -> None:
    client = client_for(
        market_frame, fetched_at, market_frame_loader=lambda source: market_frame,
    )

    response = client.get(
        "/v1/market/snapshot",
        params={"source": "EASTMONEY"},
        headers={"X-API-Key": "test-shared-key"},
    )

    assert response.status_code == 200
    assert len(response.json()) == 3
    assert response.json()[0]["source"] == "AKSHARE_EASTMONEY_SNAPSHOT"


def test_instrument_search_uses_directory_without_loading_market_snapshot(
    monkeypatch, market_frame: pd.DataFrame, fetched_at: datetime
) -> None:
    directory_calls = []
    instrument_loader = lambda: directory_calls.append(True) \
        or pd.DataFrame(
            [
                {"code": 1.0, "name": "平安银行"},
                {"code": "600519", "name": "贵州茅台"},
            ]
        )
    client = client_for(
        market_frame, fetched_at, instrument_frame_loader=instrument_loader,
    )

    response = client.get(
        "/v1/instruments/search",
        params={"query": "平安"},
        headers={"X-API-Key": "test-shared-key"},
    )

    assert response.status_code == 200
    assert response.json()[0]["instrumentId"] == "SZSE:000001"
    assert directory_calls == [True]


def test_single_quotes_are_normalized_and_cached(
    monkeypatch, fetched_at: datetime, market_frame: pd.DataFrame
) -> None:
    calls = []
    single_frame = pd.DataFrame(
        [
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
    client = client_for(
        market_frame, fetched_at,
        single_frame_loader=lambda source, symbol:
            calls.append((source.value, symbol)) or single_frame,
    )
    payload = {"source": "XUEQIU", "symbols": ["SSE:600000"]}
    headers = {"X-API-Key": "test-shared-key"}

    first = client.post("/v1/quotes/single", json=payload, headers=headers)
    second = client.post("/v1/quotes/single", json=payload, headers=headers)

    assert first.status_code == 200
    assert first.json()[0]["source"] == "AKSHARE_XUEQIU_SINGLE"
    assert second.status_code == 200
    assert calls == [("XUEQIU", "SSE:600000")]
    statuses = client.get("/v1/sources/status", headers=headers).json()
    assert next(item for item in statuses if item["source"] == "SINGLE_XUEQIU")["status"] == "UP"


def test_new_source_endpoints_require_api_key(
    market_frame: pd.DataFrame, fetched_at: datetime
) -> None:
    client = client_for(market_frame, fetched_at)

    assert client.get("/v1/market/snapshot", params={"source": "SINA"}).status_code == 401
    assert client.post(
        "/v1/quotes/single",
        json={"source": "EASTMONEY", "symbols": ["SSE:600000"]},
    ).status_code == 401
    assert client.get("/v1/sources/status").status_code == 401


def test_single_quote_cache_is_bounded(
    monkeypatch, fetched_at: datetime, market_frame: pd.DataFrame
) -> None:
    calls = []
    single_frame = pd.DataFrame(
        [
            {"item": "最新", "value": 10.2}, {"item": "昨收", "value": 10},
            {"item": "今开", "value": 10}, {"item": "最高", "value": 10.3},
            {"item": "最低", "value": 9.9}, {"item": "涨跌", "value": 0.2},
            {"item": "涨幅", "value": 2}, {"item": "总手", "value": 100},
        ]
    )
    settings = Settings(
        api_key="test-shared-key", single_cache_max_entries=50,
        cache_ttl_seconds=3, max_stale_seconds=30, search_limit=20,
    )
    client = TestClient(create_app(
        settings=settings, frame_loader=lambda: market_frame, utcnow=lambda: fetched_at,
        single_frame_loader=lambda source, symbol: calls.append(symbol) or single_frame,
    ))
    headers = {"X-API-Key": "test-shared-key"}
    first_fifty = [f"SSE:{code:06d}" for code in range(600000, 600050)]

    assert client.post("/v1/quotes/single", headers=headers,
                       json={"source": "EASTMONEY", "symbols": first_fifty}).status_code == 200
    assert client.post("/v1/quotes/single", headers=headers,
                       json={"source": "EASTMONEY", "symbols": ["SSE:600050"]}).status_code == 200
    assert client.post("/v1/quotes/single", headers=headers,
                       json={"source": "EASTMONEY", "symbols": ["SSE:600000"]}).status_code == 200
    assert calls.count("SSE:600000") == 2


def test_default_requests_timeout_is_applied(monkeypatch) -> None:
    captured = {}

    def fake_request(session, method, url, **kwargs):
        captured.update(kwargs)
        return object()

    monkeypatch.setattr(http_timeout, "_original_request", fake_request)
    http_timeout.install_default_requests_timeout(7)

    response = __import__("requests").Session().request("GET", "https://example.invalid")

    assert response is not None
    assert captured["timeout"] == 7


def test_isolated_operation_enforces_total_deadline() -> None:
    started = monotonic()

    with pytest.raises(TimeoutError, match="总时限"):
        run_isolated(
            "market", timeout_seconds=0.1, _worker_target=slow_isolated_worker,
        )

    assert monotonic() - started < 2


def test_single_quote_batch_stops_at_shared_deadline(
    market_frame: pd.DataFrame, fetched_at: datetime
) -> None:
    calls = []
    single_frame = pd.DataFrame(
        [
            {"item": "最新", "value": 10.2}, {"item": "昨收", "value": 10},
            {"item": "今开", "value": 10}, {"item": "最高", "value": 10.3},
            {"item": "最低", "value": 9.9}, {"item": "涨跌", "value": 0.2},
            {"item": "涨幅", "value": 2}, {"item": "总手", "value": 100},
        ]
    )

    def slow_loader(source, symbol):
        calls.append(symbol)
        sleep(0.08)
        return single_frame

    settings = Settings(
        api_key="test-shared-key", upstream_timeout_seconds=1,
        cache_ttl_seconds=3, max_stale_seconds=30, search_limit=20,
    )
    client = TestClient(create_app(
        settings=settings, frame_loader=lambda: market_frame, utcnow=lambda: fetched_at,
        single_frame_loader=slow_loader,
    ))
    symbols = [f"SSE:{code:06d}" for code in range(600000, 600050)]
    started = monotonic()

    response = client.post(
        "/v1/quotes/single", headers={"X-API-Key": "test-shared-key"},
        json={"source": "EASTMONEY", "symbols": symbols},
    )

    assert response.status_code == 503
    assert monotonic() - started < 2
    assert len(calls) < 50
