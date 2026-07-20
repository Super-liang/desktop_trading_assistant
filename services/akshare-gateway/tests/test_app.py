from datetime import datetime

import pandas as pd
from fastapi.testclient import TestClient

from akshare_gateway.app import Settings, create_app, load_akshare_frame


def client_for(market_frame: pd.DataFrame, fetched_at: datetime) -> TestClient:
    settings = Settings(
        api_key="test-shared-key",
        cache_ttl_seconds=3,
        max_stale_seconds=30,
        search_limit=20,
    )
    return TestClient(
        create_app(settings=settings, frame_loader=lambda: market_frame, utcnow=lambda: fetched_at)
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
