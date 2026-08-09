from datetime import date, datetime, timezone
from time import monotonic, sleep

import pandas as pd
import pytest
from fastapi.testclient import TestClient

from akshare_gateway import app as app_module
from akshare_gateway.app import (
    Settings, create_app, load_akshare_frame, load_instrument_frame, load_market_frame,
)
from akshare_gateway.contracts import CatalogMarket
from akshare_gateway.sources import MarketSource, SingleSource
from akshare_gateway import http_timeout
from akshare_gateway.isolation import run_isolated
from akshare_gateway.single_quote_cache import RedisSingleQuoteCache
from test_cache import FakeRedis
from akshare_gateway.calendar_provider import MarketSession


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
            utcnow=lambda: fetched_at,
            single_quote_cache=loaders.pop(
                "single_quote_cache", RedisSingleQuoteCache(FakeRedis())
            ),
            **loaders,
        )
    )


def test_legacy_a_share_loader_uses_only_sina(monkeypatch, market_frame: pd.DataFrame) -> None:
    import akshare as ak
    monkeypatch.setattr(ak, "stock_zh_a_spot", lambda: market_frame)

    assert load_akshare_frame() is market_frame


def test_hk_catalog_loader_falls_back_to_sina(monkeypatch) -> None:
    import akshare as ak

    monkeypatch.setattr(
        ak, "stock_hk_spot_em",
        lambda: (_ for _ in ()).throw(ConnectionError("eastmoney unavailable")),
    )
    monkeypatch.setattr(
        ak, "stock_hk_spot",
        lambda: pd.DataFrame([{"代码": "00700", "中文名称": "腾讯控股"}]),
    )

    result = load_instrument_frame(CatalogMarket.HK_STOCK)

    assert result.to_dict(orient="records") == [{"代码": "00700", "名称": "腾讯控股"}]


def test_us_catalog_loader_falls_back_to_sina_and_preserves_exchange(monkeypatch) -> None:
    import akshare as ak

    monkeypatch.setattr(
        ak, "stock_us_spot_em",
        lambda: (_ for _ in ()).throw(ConnectionError("eastmoney unavailable")),
    )
    monkeypatch.setattr(
        ak, "stock_us_spot",
        lambda: pd.DataFrame([
            {"symbol": "AAPL", "cname": "苹果公司", "name": "Apple Inc", "market": "NASDAQ"},
            {"symbol": "BRK.B", "cname": "", "name": "Berkshire Hathaway", "market": "NYSE"},
            {"symbol": "SPY", "cname": "标普 ETF", "name": "SPDR S&P 500 ETF", "market": "AMEX"},
            {"symbol": "OTCM", "cname": "场外证券", "name": "OTC Markets", "market": "OTC"},
        ]),
    )

    result = load_instrument_frame(CatalogMarket.US_STOCK)

    assert result.to_dict(orient="records") == [
        {"代码": "105.AAPL", "名称": "苹果公司"},
        {"代码": "106.BRK.B", "名称": "Berkshire Hathaway"},
        {"代码": "107.SPY", "名称": "标普 ETF"},
    ]


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


def test_calendar_endpoint_returns_normalized_sessions(
    market_frame: pd.DataFrame, fetched_at: datetime
) -> None:
    class FakeCalendarProvider:
        def sessions(self, start: date, end: date):
            assert start == end == date(2026, 7, 29)
            return [MarketSession(
                market="A_SHARE",
                trading_date=start,
                timezone="Asia/Shanghai",
                open_at=datetime(2026, 7, 29, 1, 30, tzinfo=timezone.utc),
                break_start_at=datetime(2026, 7, 29, 3, 30, tzinfo=timezone.utc),
                break_end_at=datetime(2026, 7, 29, 5, 0, tzinfo=timezone.utc),
                close_at=datetime(2026, 7, 29, 7, 0, tzinfo=timezone.utc),
                early_close=False,
            )]

    client = client_for(market_frame, fetched_at, calendar_provider=FakeCalendarProvider())
    response = client.get(
        "/v1/calendars/sessions",
        params={"start": "2026-07-29", "end": "2026-07-29"},
        headers={"X-API-Key": "test-shared-key"},
    )

    assert response.status_code == 200
    assert response.json()[0]["market"] == "A_SHARE"
    assert response.json()[0]["break_start_at"] == "2026-07-29T03:30:00+00:00"


def test_a_share_calendar_cross_check_exposes_mismatch_and_health(
    market_frame: pd.DataFrame, fetched_at: datetime
) -> None:
    client = client_for(
        market_frame, fetched_at,
        a_share_calendar_loader=lambda: pd.DataFrame({
            "trade_date": [date(2024, 11, 28)]
        }),
    )
    headers = {"X-API-Key": "test-shared-key"}

    response = client.get(
        "/v1/calendars/a-share-check",
        params={"start": "2024-11-28", "end": "2024-11-29"},
        headers=headers,
    )

    assert response.status_code == 200
    assert response.json()["status"] == "MISMATCH"
    assert response.json()["onlyExchangeCalendars"] == ["2024-11-29"]
    statuses = client.get("/v1/sources/status", headers=headers).json()
    assert next(item for item in statuses
                if item["source"] == "CALENDAR_A_SHARE_CHECK")["status"] == "UP"


def test_a_share_calendar_check_fails_closed_on_schema_drift(
    market_frame: pd.DataFrame, fetched_at: datetime
) -> None:
    client = client_for(
        market_frame, fetched_at,
        a_share_calendar_loader=lambda: pd.DataFrame({"unexpected": []}),
    )

    response = client.get(
        "/v1/calendars/a-share-check",
        params={"start": "2024-11-28", "end": "2024-11-29"},
        headers={"X-API-Key": "test-shared-key"},
    )

    assert response.status_code == 503


def test_index_endpoint_keeps_product_order_and_explicit_missing_cards(
    market_frame: pd.DataFrame, fetched_at: datetime
) -> None:
    index_frame = pd.DataFrame([
        {"代码": "000001", "名称": "上证指数", "最新价": 3100, "涨跌额": 1,
         "涨跌幅": 0.03, "今开": 3098, "昨收": 3099},
        {"代码": "399001", "名称": "深证成指", "最新价": 9900, "涨跌额": -2,
         "涨跌幅": -0.02, "今开": 9910, "昨收": 9902},
        {"代码": "399006", "名称": "创业板指", "最新价": 2000, "涨跌额": 3,
         "涨跌幅": 0.15, "今开": 1995, "昨收": 1997},
    ])
    client = client_for(
        market_frame, fetched_at,
        index_frames_loader=lambda source: [index_frame],
    )

    response = client.get("/v1/market/indices", headers={"X-API-Key": "test-shared-key"})

    assert response.status_code == 200
    cards = response.json()
    assert [card["name"] for card in cards[:3]] == ["上证指数", "深证成指", "创业板指"]
    assert len(cards) == 7
    assert cards[3]["name"] == "北证50"
    assert cards[3]["available"] is False


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
    with pytest.raises(ValueError, match="不支持"):
        load_market_frame(MarketSource.EASTMONEY)
    assert calls == ["sina"]


def test_hk_sina_snapshot_retries_once_without_eastmoney(
    monkeypatch, market_frame: pd.DataFrame
) -> None:
    import akshare as ak

    calls = []
    def transient_failure():
        calls.append("sina")
        if len(calls) == 1:
            raise ConnectionError("transient")
        return market_frame
    monkeypatch.setattr(ak, "stock_hk_spot", transient_failure)
    monkeypatch.setattr(ak, "stock_hk_spot_em",
                        lambda: (_ for _ in ()).throw(AssertionError("不应调用东财")))

    assert load_market_frame(MarketSource.SINA, CatalogMarket.HK_STOCK) is market_frame
    assert calls == ["sina", "sina"]


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
        params={"source": "SINA"},
        headers={"X-API-Key": "test-shared-key"},
    )

    assert response.status_code == 200
    assert len(response.json()) == 3
    assert response.json()[0]["source"] == "AKSHARE_SINA_SNAPSHOT"


def test_hk_sina_market_snapshot_uses_explicit_market_adapter(
    market_frame: pd.DataFrame, fetched_at: datetime
) -> None:
    frames = {
        (CatalogMarket.HK_STOCK, MarketSource.SINA): pd.DataFrame([{
            "代码": "700", "中文名称": "腾讯控股", "最新价": 500, "涨跌额": 5,
            "涨跌幅": 1, "今开": 496, "昨收": 495, "最高": 501,
            "最低": 494, "成交量": 1000,
        }]),
    }
    client = client_for(
        market_frame, fetched_at,
        market_frame_loader=lambda source, market: frames[(market, source)],
    )
    headers = {"X-API-Key": "test-shared-key"}

    hk = client.get("/v1/market/snapshot", params={
        "market": "HK_STOCK", "source": "SINA"}, headers=headers)
    assert hk.status_code == 200
    assert hk.json()[0]["instrumentId"] == "HKEX:00700"


def test_us_market_snapshot_is_rejected_without_upstream_call(
    market_frame: pd.DataFrame, fetched_at: datetime
) -> None:
    calls = []
    client = client_for(
        market_frame, fetched_at,
        market_frame_loader=lambda source, market: calls.append((source, market)),
    )

    response = client.get("/v1/market/snapshot", params={
        "market": "US_STOCK", "source": "SINA"},
        headers={"X-API-Key": "test-shared-key"})

    assert response.status_code == 422
    assert calls == []


def test_market_snapshot_rejects_removed_eastmoney_without_upstream_call(
    market_frame: pd.DataFrame, fetched_at: datetime
) -> None:
    calls = []
    client = client_for(
        market_frame, fetched_at,
        market_frame_loader=lambda source, market: calls.append((source, market)),
    )

    response = client.get("/v1/market/snapshot", params={
        "market": "HK_STOCK", "source": "EASTMONEY"},
        headers={"X-API-Key": "test-shared-key"})

    assert response.status_code == 422
    assert calls == []


def test_source_capabilities_are_market_scoped_and_match_implemented_routes(
    market_frame: pd.DataFrame, fetched_at: datetime
) -> None:
    client = client_for(market_frame, fetched_at)

    response = client.get(
        "/v1/sources/capabilities",
        headers={"X-API-Key": "test-shared-key"},
    )

    assert response.status_code == 200
    capabilities = response.json()
    assert {item["sourceId"] for item in capabilities if item["capability"] == "SNAPSHOT"} == {
        "A_SHARE:SNAPSHOT:SINA", "HK_STOCK:SNAPSHOT:SINA",
    }
    assert any(item["sourceId"] == "US_STOCK:POSITION:SINA"
               for item in capabilities)
    assert any(item["sourceId"] == "PUBLIC_FUND:UNIT_NAV:EASTMONEY"
               for item in capabilities)


def test_us_position_quotes_validate_then_call_injected_sina_loader(
    market_frame: pd.DataFrame, fetched_at: datetime
) -> None:
    calls = []
    quote = {
        "instrumentId": "NASDAQ:AAPL", "name": "Apple", "last": 200,
        "previousClose": 198, "open": 199, "high": 201, "low": 197,
        "change": 2, "changePercent": 1.01, "volume": 100,
        "marketPhase": "UNKNOWN", "source": "AKSHARE_SINA_POSITION",
        "sourceTimestamp": fetched_at, "receivedAt": fetched_at,
        "delayed": True, "stale": False, "demo": False,
    }
    client = client_for(
        market_frame, fetched_at,
        us_position_quote_loader=lambda symbols: calls.append(symbols) or [quote],
    )
    headers = {"X-API-Key": "test-shared-key"}

    success = client.post("/v1/quotes/us-positions", headers=headers, json={
        "symbols": ["nasdaq:aapl", "NASDAQ:AAPL"]})
    invalid = client.post("/v1/quotes/us-positions", headers=headers, json={
        "symbols": ["NASDAQ:AAPL;DROP"]})

    assert success.status_code == 200
    assert success.json()[0]["source"] == "AKSHARE_SINA_POSITION"
    assert invalid.status_code == 422
    assert calls == [["NASDAQ:AAPL"]]


def test_fund_unit_nav_uses_current_data_and_history_for_missing_holding(
    market_frame: pd.DataFrame, fetched_at: datetime
) -> None:
    history_calls = []
    client = client_for(
        market_frame, fetched_at,
        fund_nav_loader=lambda: pd.DataFrame([{
            "基金代码": "000001", "基金简称": "当前基金",
            "2026-07-29-单位净值": "1.25", "2026-07-28-单位净值": "1.20",
        }]),
        fund_history_loader=lambda code: history_calls.append(code) or pd.DataFrame([
            {"净值日期": "2026-07-29", "单位净值": "2.1"},
            {"净值日期": "2026-07-28", "单位净值": "2.0"},
        ]),
    )

    response = client.post(
        "/v1/funds/unit-nav",
        json={"symbols": ["CN_FUND:000001", "CN_FUND:000002"]},
        headers={"X-API-Key": "test-shared-key"},
    )

    assert response.status_code == 200
    assert [item["instrumentId"] for item in response.json()] == [
        "CN_FUND:000001", "CN_FUND:000002"]
    assert history_calls == ["000002"]
    assert response.json()[1]["previousUnitNav"] == 2.0


def test_all_fund_unit_nav_returns_each_fund_once(
    market_frame: pd.DataFrame, fetched_at: datetime
) -> None:
    client = client_for(
        market_frame, fetched_at,
        fund_nav_loader=lambda: pd.DataFrame([{
            "基金代码": "000001", "基金简称": "当前基金",
            "2026-07-29-单位净值": "1.25", "2026-07-28-单位净值": "1.20",
        }]),
    )

    response = client.get(
        "/v1/funds/unit-nav", headers={"X-API-Key": "test-shared-key"})

    assert response.status_code == 200
    assert [item["instrumentId"] for item in response.json()] == ["CN_FUND:000001"]


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


def test_instrument_catalog_returns_normalized_full_directory(
    market_frame: pd.DataFrame, fetched_at: datetime
) -> None:
    client = client_for(
        market_frame,
        fetched_at,
        instrument_frame_loader=lambda: pd.DataFrame(
            [
                {"code": 1.0, "name": "平安银行"},
                {"code": "600519", "name": "贵州茅台"},
            ]
        ),
    )

    response = client.get(
        "/v1/instruments/catalog", headers={"X-API-Key": "test-shared-key"}
    )

    assert response.status_code == 200
    assert response.json() == [
        {
            "instrumentId": "SZSE:000001",
            "code": "000001",
            "name": "平安银行",
            "market": "A_SHARE",
            "exchange": "SZSE",
            "currency": "CNY",
            "assetType": "STOCK",
            "providerSymbol": "000001",
        },
        {
            "instrumentId": "SSE:600519",
            "code": "600519",
            "name": "贵州茅台",
            "market": "A_SHARE",
            "exchange": "SSE",
            "currency": "CNY",
            "assetType": "STOCK",
            "providerSymbol": "600519",
        },
    ]


def test_catalog_isolated_by_explicit_market(
    market_frame: pd.DataFrame, fetched_at: datetime
) -> None:
    frames = {
        CatalogMarket.A_SHARE: pd.DataFrame([{"code": "600519", "name": "贵州茅台"}]),
        CatalogMarket.HK_STOCK: pd.DataFrame([{"代码": "700", "名称": "腾讯控股"}]),
        CatalogMarket.US_STOCK: pd.DataFrame([{"代码": "105.AAPL", "名称": "Apple"}]),
        CatalogMarket.PUBLIC_FUND: pd.DataFrame([
            {"基金代码": "000001", "基金简称": "开放基金", "基金类型": "混合型"}
        ]),
    }
    client = client_for(
        market_frame, fetched_at,
        instrument_frame_loader=lambda market: frames[market],
    )
    headers = {"X-API-Key": "test-shared-key"}

    hk = client.get("/v1/instruments/catalog", params={"market": "HK_STOCK"}, headers=headers)
    us_search = client.get(
        "/v1/instruments/search",
        params={"market": "US_STOCK", "query": "AAPL"},
        headers=headers,
    )

    assert hk.status_code == 200
    assert hk.json()[0]["instrumentId"] == "HKEX:00700"
    assert hk.json()[0]["currency"] == "HKD"
    assert us_search.status_code == 200
    assert us_search.json()[0]["instrumentId"] == "NASDAQ:AAPL"
    assert all(item["market"] == "US_STOCK" for item in us_search.json())


def test_missing_market_keeps_legacy_a_share_behavior(
    market_frame: pd.DataFrame, fetched_at: datetime
) -> None:
    client = client_for(
        market_frame, fetched_at,
        instrument_frame_loader=lambda market: pd.DataFrame([
            {"code": "600519", "name": "贵州茅台"}
        ]) if market == CatalogMarket.A_SHARE else pd.DataFrame(),
    )

    response = client.get(
        "/v1/instruments/search", params={"query": "600519"},
        headers={"X-API-Key": "test-shared-key"},
    )

    assert response.status_code == 200
    assert response.json()[0]["market"] == "A_SHARE"


def test_public_fund_loader_intersects_open_nav_codes(monkeypatch) -> None:
    import akshare as ak

    monkeypatch.setattr(ak, "fund_name_em", lambda: pd.DataFrame([
        {"基金代码": "000001", "基金简称": "开放基金", "基金类型": "混合型"},
        {"基金代码": "999999", "基金简称": "封闭基金", "基金类型": "封闭式"},
    ]))
    monkeypatch.setattr(ak, "fund_open_fund_daily_em", lambda: pd.DataFrame([
        {"基金代码": "000001", "2026-07-28-单位净值": "1.25"},
    ]))

    result = load_instrument_frame(CatalogMarket.PUBLIC_FUND)

    assert result["基金代码"].tolist() == ["000001"]


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


def test_single_quote_failure_returns_same_source_last_value_as_stale(
    fetched_at: datetime, market_frame: pd.DataFrame
) -> None:
    redis = FakeRedis()
    persistent_cache = RedisSingleQuoteCache(redis)
    frame = pd.DataFrame(
        [
            {"item": "最新", "value": 10.2}, {"item": "昨收", "value": 10},
            {"item": "今开", "value": 10}, {"item": "最高", "value": 10.3},
            {"item": "最低", "value": 9.9}, {"item": "涨跌", "value": 0.2},
            {"item": "涨幅", "value": 2}, {"item": "总手", "value": 100},
        ]
    )
    failing = False

    def loader(source, symbol):
        if failing:
            raise ConnectionError("upstream unavailable")
        return frame

    client = client_for(
        market_frame, fetched_at, single_frame_loader=loader,
        single_quote_cache=persistent_cache,
    )
    headers = {"X-API-Key": "test-shared-key"}
    payload = {"source": "EASTMONEY", "symbols": ["SSE:600000"]}
    first = client.post("/v1/quotes/single", headers=headers, json=payload)
    assert first.status_code == 200
    assert first.json()[0]["lastSuccessAt"] == fetched_at.isoformat().replace("+00:00", "Z")

    failing = True
    # 新建应用模拟进程重启，确保回退值不是来自进程内缓存。
    restarted = client_for(
        market_frame, fetched_at, single_frame_loader=loader,
        single_quote_cache=RedisSingleQuoteCache(redis),
    )
    fallback = restarted.post("/v1/quotes/single", headers=headers, json=payload)

    assert fallback.status_code == 200
    assert fallback.json()[0]["last"] == 10.2
    assert fallback.json()[0]["stale"] is True
    assert fallback.json()[0]["source"] == "AKSHARE_EASTMONEY_SINGLE"
    assert fallback.json()[0]["sourceTimestamp"] == first.json()[0]["sourceTimestamp"]
    assert fallback.json()[0]["lastSuccessAt"] == first.json()[0]["lastSuccessAt"]


def test_single_quote_never_falls_back_to_another_source(
    fetched_at: datetime, market_frame: pd.DataFrame
) -> None:
    redis = FakeRedis()
    persistent_cache = RedisSingleQuoteCache(redis)
    persistent_cache.save(
        "XUEQIU", "SSE:600000",
        {
            "instrumentId": "SSE:600000", "last": 99,
            "source": "AKSHARE_XUEQIU_SINGLE", "sourceTimestamp": fetched_at,
        },
        fetched_at,
    )
    client = client_for(
        market_frame, fetched_at,
        single_frame_loader=lambda source, symbol: (_ for _ in ()).throw(
            ConnectionError("eastmoney unavailable")
        ),
        single_quote_cache=persistent_cache,
    )

    response = client.post(
        "/v1/quotes/single",
        headers={"X-API-Key": "test-shared-key"},
        json={"source": "EASTMONEY", "symbols": ["SSE:600000"]},
    )

    assert response.status_code == 503
    assert response.json()["detail"] == "所选单股行情源暂无可用数据"


def test_single_quotes_keep_successful_symbols_when_one_upstream_fails(
    fetched_at: datetime, market_frame: pd.DataFrame
) -> None:
    single_frame = pd.DataFrame(
        [
            {"item": "最新", "value": 10.2}, {"item": "昨收", "value": 10},
            {"item": "今开", "value": 10}, {"item": "最高", "value": 10.3},
            {"item": "最低", "value": 9.9}, {"item": "涨跌", "value": 0.2},
            {"item": "涨幅", "value": 2}, {"item": "总手", "value": 100},
        ]
    )

    def partly_unavailable(source, symbol):
        if symbol == "SSE:600001":
            raise ConnectionError("upstream unavailable")
        return single_frame

    client = client_for(
        market_frame, fetched_at, single_frame_loader=partly_unavailable,
    )
    response = client.post(
        "/v1/quotes/single",
        headers={"X-API-Key": "test-shared-key"},
        json={"source": "EASTMONEY", "symbols": ["SSE:600000", "SSE:600001"]},
    )

    assert response.status_code == 200
    assert [quote["instrumentId"] for quote in response.json()] == ["SSE:600000"]


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
    assert client.get("/v1/instruments/catalog").status_code == 401


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
        single_quote_cache=RedisSingleQuoteCache(FakeRedis()),
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
        single_quote_cache=RedisSingleQuoteCache(FakeRedis()),
    ))
    symbols = [f"SSE:{code:06d}" for code in range(600000, 600050)]
    started = monotonic()

    response = client.post(
        "/v1/quotes/single", headers={"X-API-Key": "test-shared-key"},
        json={"source": "EASTMONEY", "symbols": symbols},
    )

    assert response.status_code == 200
    assert monotonic() - started < 2
    assert 0 < len(response.json()) < 50
    assert len(calls) < 50
