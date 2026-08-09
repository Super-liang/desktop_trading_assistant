from __future__ import annotations

import hmac
import io
import logging
import os
import re
from collections import OrderedDict
from concurrent.futures import ThreadPoolExecutor
from contextlib import redirect_stderr
from contextvars import ContextVar
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from threading import Lock
from time import monotonic, sleep
from typing import Callable

import pandas as pd
from fastapi import Body, Depends, FastAPI, Header, HTTPException, Query
from pydantic import BaseModel, Field

from .cache import SnapshotCache, UpstreamUnavailable
from .calendar_provider import ExchangeCalendarProvider
from .contracts import CatalogMarket, INDEX_DEFINITIONS, normalize_catalog, normalize_index_frames
from .http_timeout import install_default_requests_timeout
from .funds import normalize_current_unit_nav, normalize_fund_history
from .isolation import run_isolated
from .market import (
    InvalidMarketData,
    asset_type_for,
    build_market_snapshot,
    build_single_quote,
    exchange_for,
    normalize_code,
)
from .multi_market import build_cross_market_snapshot
from .sources import (
    MarketSource,
    SOURCE_CAPABILITIES,
    SingleSource,
    SourceHealthRegistry,
)
from .single_quote_cache import RedisSingleQuoteCache, SingleQuoteCache
from .us_position_quotes import validate_us_instruments

SYMBOL_PATTERN = re.compile(r"^(SSE|SZSE|BSE):(\d{6})$")
log = logging.getLogger(__name__)


@dataclass(frozen=True)
class Settings:
    api_key: str
    cache_ttl_seconds: float = 10
    max_stale_seconds: float = 30
    search_limit: int = 20
    upstream_timeout_seconds: float = 30
    catalog_timeout_seconds: float = 900
    single_cache_max_entries: int = 1000
    redis_url: str | None = None

    @classmethod
    def from_env(cls) -> "Settings":
        return cls(
            api_key=os.getenv("AKSHARE_API_KEY", ""),
            cache_ttl_seconds=float(os.getenv("AKSHARE_CACHE_TTL_SECONDS", "10")),
            max_stale_seconds=float(os.getenv("AKSHARE_MAX_STALE_SECONDS", "30")),
            search_limit=int(os.getenv("AKSHARE_SEARCH_LIMIT", "20")),
            upstream_timeout_seconds=float(
                os.getenv("AKSHARE_UPSTREAM_TIMEOUT_SECONDS", "30")
            ),
            catalog_timeout_seconds=float(
                os.getenv("AKSHARE_CATALOG_TIMEOUT_SECONDS", "900")
            ),
            single_cache_max_entries=int(
                os.getenv("AKSHARE_SINGLE_CACHE_MAX_ENTRIES", "1000")
            ),
            redis_url=os.getenv(
                "AKSHARE_REDIS_URL", "redis://127.0.0.1:6379/0"
            ),
        )

    def validate(self) -> None:
        if not self.api_key.strip():
            raise RuntimeError("必须配置 AKSHARE_API_KEY")
        if not 1 <= self.search_limit <= 100:
            raise RuntimeError("AKSHARE_SEARCH_LIMIT 必须在 1 到 100 之间")
        if not 1 <= self.upstream_timeout_seconds <= 120:
            raise RuntimeError("AKSHARE_UPSTREAM_TIMEOUT_SECONDS 必须在 1 到 120 之间")
        if not self.upstream_timeout_seconds <= self.catalog_timeout_seconds <= 1800:
            raise RuntimeError(
                "AKSHARE_CATALOG_TIMEOUT_SECONDS 必须不小于上游请求超时且不大于 1800"
            )
        if not 50 <= self.single_cache_max_entries <= 10000:
            raise RuntimeError("AKSHARE_SINGLE_CACHE_MAX_ENTRIES 必须在 50 到 10000 之间")


def load_akshare_frame() -> pd.DataFrame:
    # 延迟导入避免健康检查和单元测试触发上游 SDK 初始化。
    import akshare as ak
    return _load_sina_with_retry("A_SHARE", ak.stock_zh_a_spot)


def _load_sina_with_retry(market: str, loader: Callable[[], pd.DataFrame]) -> pd.DataFrame:
    for attempt in range(2):
        try:
            return loader()
        except Exception as exception:
            if attempt == 1:
                raise
            log.warning("新浪全市场接口瞬时失败，执行一次重试：market=%s,error=%s",
                        market, type(exception).__name__)
            sleep(0.25)
    raise RuntimeError("新浪全市场接口重试失败")


def load_market_frame(
    source: MarketSource, market: CatalogMarket = CatalogMarket.A_SHARE
) -> pd.DataFrame:
    import akshare as ak
    if source != MarketSource.SINA or market not in {
        CatalogMarket.A_SHARE, CatalogMarket.HK_STOCK
    }:
        raise ValueError("所选市场不支持该全市场行情源")
    if market == CatalogMarket.A_SHARE:
        return _load_sina_with_retry("A_SHARE", ak.stock_zh_a_spot)
    if market == CatalogMarket.HK_STOCK:
        # 新浪港股接口会输出分页进度条，服务端任务不写入 systemd 日志。
        with redirect_stderr(io.StringIO()):
            return _load_sina_with_retry("HK_STOCK", ak.stock_hk_spot)
    raise ValueError("所选市场不支持该全市场行情源")


def load_single_frame(source: SingleSource, instrument_id: str) -> pd.DataFrame:
    import akshare as ak

    exchange, code = instrument_id.split(":", 1)
    if source == SingleSource.EASTMONEY:
        if exchange == "BSE":
            raise ValueError("东财 stock_bid_ask_em 不支持北交所 market id")
        return ak.stock_bid_ask_em(symbol=code)
    prefix = {"SSE": "SH", "SZSE": "SZ", "BSE": "BJ"}[exchange]
    return ak.stock_individual_spot_xq(symbol=f"{prefix}{code}")


def load_instrument_frame(market: CatalogMarket = CatalogMarket.A_SHARE) -> pd.DataFrame:
    import akshare as ak

    if market == CatalogMarket.A_SHARE:
        return ak.stock_info_a_code_name()
    if market == CatalogMarket.HK_STOCK:
        try:
            frame = ak.stock_hk_spot_em()
            if frame.empty:
                raise ValueError("东方财富港股目录为空")
            return frame[["代码", "名称"]]
        except Exception as exception:
            log.warning(
                "AKShare 东方财富港股目录失败，切换新浪：error=%s",
                type(exception).__name__,
            )
            # AKShare 目录函数会输出分页进度条，服务端任务不写入 systemd 日志。
            with redirect_stderr(io.StringIO()):
                frame = ak.stock_hk_spot()
            return frame[["代码", "中文名称"]].rename(
                columns={"中文名称": "名称"}
            )
    if market == CatalogMarket.US_STOCK:
        try:
            frame = ak.stock_us_spot_em()
            if frame.empty:
                raise ValueError("东方财富美股目录为空")
            return frame[["代码", "名称"]]
        except Exception as exception:
            log.warning(
                "AKShare 东方财富美股目录失败，切换新浪：error=%s",
                type(exception).__name__,
            )
            with redirect_stderr(io.StringIO()):
                frame = ak.stock_us_spot().copy()
            required = {"symbol", "cname", "name", "market"}
            missing = required.difference(str(column) for column in frame.columns)
            if missing:
                raise RuntimeError(
                    f"AKShare 新浪美股目录缺少列: {', '.join(sorted(missing))}"
                )
            market_codes = {"NASDAQ": "105", "NYSE": "106", "AMEX": "107"}
            frame["market"] = frame["market"].astype(str).str.upper().str.strip()
            frame = frame[frame["market"].isin(market_codes)].copy()
            frame["symbol"] = frame["symbol"].astype(str).str.upper().str.strip()
            chinese_name = frame["cname"].fillna("").astype(str).str.strip()
            english_name = frame["name"].fillna("").astype(str).str.strip()
            frame["名称"] = chinese_name.where(chinese_name.ne(""), english_name)
            frame["代码"] = frame["market"].map(market_codes) + "." + frame["symbol"]
            return frame[["代码", "名称"]]
    names = ak.fund_name_em()
    # 以开放式基金单位净值接口的代码集合限定首期目录口径，避免混入封闭式基金。
    open_codes = ak.fund_open_fund_daily_em()[["基金代码"]].drop_duplicates()
    return names.merge(open_codes, on="基金代码", how="inner")


def load_index_frames(source: MarketSource) -> list[pd.DataFrame]:
    import akshare as ak

    if source == MarketSource.SINA:
        return [ak.stock_zh_index_spot_sina()]
    groups = tuple(dict.fromkeys(
        group for definition in INDEX_DEFINITIONS for group in definition.groups
    ))
    # 各分组是独立网络请求，用有界线程池缩短首页指数快照刷新时间。
    with ThreadPoolExecutor(max_workers=min(4, len(groups)), thread_name_prefix="index-group") as pool:
        return list(pool.map(lambda group: ak.stock_zh_index_spot_em(symbol=group), groups))


def load_a_share_trade_dates() -> pd.DataFrame:
    import akshare as ak

    return ak.tool_trade_date_hist_sina()


def load_fund_nav_frame() -> pd.DataFrame:
    import akshare as ak

    return ak.fund_open_fund_daily_em()


def load_fund_history_frame(code: str) -> pd.DataFrame:
    import akshare as ak

    return ak.fund_open_fund_info_em(symbol=code, indicator="单位净值走势")


class SingleQuoteRequest(BaseModel):
    source: SingleSource
    symbols: list[str] = Field(min_length=1, max_length=50)


class FundNavRequest(BaseModel):
    symbols: list[str] = Field(min_length=1, max_length=100)


class UsPositionQuoteRequest(BaseModel):
    symbols: list[str] = Field(min_length=1, max_length=2000)


def create_app(
    settings: Settings | None = None,
    frame_loader: Callable[[], pd.DataFrame] | None = None,
    utcnow: Callable[[], datetime] | None = None,
    market_frame_loader: Callable[..., pd.DataFrame] | None = None,
    single_frame_loader: Callable[[SingleSource, str], pd.DataFrame] | None = None,
    instrument_frame_loader: Callable[..., pd.DataFrame] | None = None,
    single_quote_cache: SingleQuoteCache | None = None,
    calendar_provider: ExchangeCalendarProvider | None = None,
    index_frames_loader: Callable[[MarketSource], list[pd.DataFrame]] | None = None,
    a_share_calendar_loader: Callable[[], pd.DataFrame] | None = None,
    fund_nav_loader: Callable[[], pd.DataFrame] | None = None,
    fund_history_loader: Callable[[str], pd.DataFrame] | None = None,
    us_position_quote_loader: Callable[[list[str]], list[dict]] | None = None,
) -> FastAPI:
    config = settings or Settings.from_env()
    config.validate()
    install_default_requests_timeout(config.upstream_timeout_seconds)
    load_frame = frame_loader or (lambda: run_isolated(
        "legacy", timeout_seconds=config.upstream_timeout_seconds
    ))
    def fetch_market_frame(source: MarketSource, market: CatalogMarket) -> pd.DataFrame:
        if market_frame_loader is not None:
            import inspect
            if len(inspect.signature(market_frame_loader).parameters) == 1:
                return market_frame_loader(source)
            return market_frame_loader(source, market)
        return run_isolated(
            "market", source=source.value, market=market.value,
            timeout_seconds=config.upstream_timeout_seconds,
        )
    single_batch_deadline: ContextVar[float | None] = ContextVar(
        "single_batch_deadline", default=None
    )

    def fetch_single_frame(source: SingleSource, instrument: str) -> pd.DataFrame:
        deadline = single_batch_deadline.get()
        remaining = config.upstream_timeout_seconds if deadline is None else deadline - monotonic()
        if remaining <= 0:
            raise TimeoutError("AKShare 单股批量操作超过总时限")
        if single_frame_loader is not None:
            return single_frame_loader(source, instrument)
        return run_isolated(
            "single", source=source.value, instrument_id=instrument,
            timeout_seconds=min(config.upstream_timeout_seconds, remaining),
        )
    def fetch_instrument_frame(market: CatalogMarket) -> pd.DataFrame:
        if instrument_frame_loader is not None:
            # 兼容 0.1.5 测试和嵌入方传入的无参 A 股目录加载器。
            import inspect
            if len(inspect.signature(instrument_frame_loader).parameters) == 0:
                return instrument_frame_loader()
            return instrument_frame_loader(market)
        return run_isolated(
            "instruments", source=market.value,
            timeout_seconds=config.catalog_timeout_seconds,
            request_timeout_seconds=config.upstream_timeout_seconds,
        )
    fetch_index_frames = index_frames_loader or (lambda source: run_isolated(
        "indices", source=source.value, timeout_seconds=config.upstream_timeout_seconds
    ))
    fetch_a_share_calendar = a_share_calendar_loader or (lambda: run_isolated(
        "a_share_calendar", timeout_seconds=config.upstream_timeout_seconds
    ))
    fetch_fund_nav = fund_nav_loader or (lambda: run_isolated(
        "fund_nav", timeout_seconds=config.upstream_timeout_seconds
    ))
    fetch_fund_history = fund_history_loader or (lambda code: run_isolated(
        "fund_history", instrument_id=code,
        timeout_seconds=config.upstream_timeout_seconds,
    ))
    fetch_us_quotes = us_position_quote_loader or (lambda symbols: run_isolated(
        "us_position_quotes", instrument_id=",".join(symbols),
        timeout_seconds=config.upstream_timeout_seconds,
    ))
    now = utcnow or (lambda: datetime.now(timezone.utc))
    if single_quote_cache is None:
        if not config.redis_url:
            raise RuntimeError("必须配置 AKSHARE_REDIS_URL")
        single_quote_cache = RedisSingleQuoteCache.from_url(config.redis_url)
    persistent_single_cache = single_quote_cache
    source_health = SourceHealthRegistry()
    calendars = calendar_provider or ExchangeCalendarProvider()
    single_caches: OrderedDict[str, SnapshotCache] = OrderedDict()
    single_caches_lock = Lock()

    def load_quotes():
        return build_market_snapshot(load_frame(), now())

    cache = SnapshotCache(
        load_quotes,
        ttl_seconds=config.cache_ttl_seconds,
        max_stale_seconds=config.max_stale_seconds,
    )
    def load_instruments(market: CatalogMarket) -> dict[str, dict[str, str]]:
        frame = fetch_instrument_frame(market)
        instruments = normalize_catalog(frame, market)
        rejected = len(frame.index) - len(instruments)
        if rejected:
            log.warning(
                "AKShare 证券目录拒绝无效行：market=%s,rejected_count=%s",
                market.value, rejected,
            )
        if not instruments:
            raise InvalidMarketData(f"AKShare {market.value}证券目录没有可用数据")
        return instruments

    instrument_caches = {
        market: SnapshotCache(
            lambda selected=market: load_instruments(selected),
            ttl_seconds=3600,
            max_stale_seconds=86400,
        )
        for market in CatalogMarket
    }
    application = FastAPI(
        title="AKShare A-share Quote Gateway",
        version="0.1.0",
        docs_url=None,
        redoc_url=None,
        openapi_url=None,
    )

    def authorize(x_api_key: str | None = Header(default=None)) -> None:
        supplied = x_api_key or ""
        if not hmac.compare_digest(supplied.encode(), config.api_key.encode()):
            raise HTTPException(status_code=401, detail="未授权")

    def current_snapshot():
        try:
            return cache.get()
        except UpstreamUnavailable as exception:
            raise HTTPException(status_code=503, detail="行情源暂不可用") from exception

    def normalize_symbol(raw_symbol: str) -> str:
        symbol = raw_symbol.strip().upper()
        match = SYMBOL_PATTERN.fullmatch(symbol)
        if not match:
            raise HTTPException(status_code=422, detail="证券标识格式无效")
        exchange, code = match.groups()
        try:
            if exchange_for(code) != exchange:
                raise HTTPException(status_code=422, detail="证券交易所与代码不匹配")
        except RuntimeError as exception:
            raise HTTPException(status_code=422, detail="证券代码无效") from exception
        return symbol

    @application.get("/health")
    def health() -> dict:
        return {"status": "UP", "source": "AKSHARE", "cache": cache.status()}

    @application.get("/v1/search", dependencies=[Depends(authorize)])
    def search(query: str = Query(min_length=1, max_length=64)) -> list[dict]:
        keyword = query.strip().upper()
        if not keyword:
            raise HTTPException(status_code=422, detail="搜索词不能为空")
        snapshot = current_snapshot()
        results = []
        for instrument_id, quote in snapshot.quotes.items():
            code = instrument_id.split(":", 1)[1]
            if keyword not in code and keyword not in quote["name"].upper():
                continue
            exchange = instrument_id.split(":", 1)[0]
            results.append(
                {
                    "instrumentId": instrument_id,
                    "code": code,
                    "name": quote["name"],
                    "exchange": exchange,
                    "assetType": asset_type_for(code),
                }
            )
            if len(results) >= config.search_limit:
                break
        return results

    @application.get("/v1/instruments/search", dependencies=[Depends(authorize)])
    def instrument_search(
        query: str = Query(min_length=1, max_length=64),
        market: CatalogMarket = CatalogMarket.A_SHARE,
    ) -> list[dict]:
        keyword = query.strip().upper()
        try:
            directory = instrument_caches[market].get().quotes
        except UpstreamUnavailable as exception:
            raise HTTPException(status_code=503, detail=f"{market.value}证券目录暂不可用") from exception
        results = []
        for instrument_id, instrument in directory.items():
            if keyword not in instrument["code"] and keyword not in instrument["name"].upper():
                continue
            results.append({"instrumentId": instrument_id, **instrument})
            if len(results) >= config.search_limit:
                break
        return results

    @application.get("/v1/instruments/catalog", dependencies=[Depends(authorize)])
    def instrument_catalog(
        market: CatalogMarket = CatalogMarket.A_SHARE,
    ) -> list[dict]:
        try:
            directory = instrument_caches[market].get().quotes
        except UpstreamUnavailable as exception:
            raise HTTPException(status_code=503, detail=f"{market.value}证券目录暂不可用") from exception
        return [
            {"instrumentId": instrument_id, **instrument}
            for instrument_id, instrument in directory.items()
        ]

    @application.get("/v1/market/snapshot", dependencies=[Depends(authorize)])
    def market_snapshot(
        source: MarketSource,
        market: CatalogMarket = CatalogMarket.A_SHARE,
    ) -> list[dict]:
        if market not in {CatalogMarket.A_SHARE, CatalogMarket.HK_STOCK} \
                or source != MarketSource.SINA:
            raise HTTPException(status_code=422, detail="所选市场不支持该全市场行情源")
        source_id = f"{market.value}:SNAPSHOT:{source.value}"
        try:
            def snapshot_operation() -> dict:
                return build_market_snapshot(
                    fetch_market_frame(source, market), now(),
                    f"AKSHARE_{source.value}_SNAPSHOT",
                ) if market == CatalogMarket.A_SHARE else build_cross_market_snapshot(
                    fetch_market_frame(source, market), market, source.value, now(),
                )

            if market == CatalogMarket.A_SHARE:
                quotes = source_health.observe(
                    source_id,
                    lambda: source_health.observe(
                        f"SNAPSHOT_{source.value}", snapshot_operation),
                )
            else:
                quotes = source_health.observe(source_id, snapshot_operation)
        except Exception as exception:
            log.warning("AKShare 全市场来源调用失败：market=%s,source=%s,error=%s",
                        market.value, source.value, type(exception).__name__)
            raise HTTPException(status_code=503, detail="所选全市场行情源暂不可用") from exception
        return list(quotes.values())

    @application.get("/v1/market/indices", dependencies=[Depends(authorize)])
    def market_indices(source: MarketSource = MarketSource.EASTMONEY) -> list[dict]:
        source_id = f"INDEX_{source.value}"
        quote_as_of = now().isoformat()
        try:
            quotes = source_health.observe(
                source_id,
                lambda: normalize_index_frames(
                    fetch_index_frames(source), source.value, quote_as_of
                ),
            )
        except Exception as exception:
            log.warning("AKShare 指数来源调用失败：source=%s,error=%s",
                        source.value, type(exception).__name__)
            raise HTTPException(status_code=503, detail="大盘指数行情源暂不可用") from exception
        return [
            quotes.get(definition.instrument_id, {
                "instrumentId": definition.instrument_id,
                "code": definition.code,
                "name": definition.name,
                "source": source.value,
                "quoteAsOf": quote_as_of,
                "available": False,
            }) | {"available": definition.instrument_id in quotes}
            for definition in INDEX_DEFINITIONS
        ]

    @application.post("/v1/quotes/single", dependencies=[Depends(authorize)])
    def single_quotes(request: SingleQuoteRequest = Body()) -> list[dict]:
        normalized = list(dict.fromkeys(normalize_symbol(symbol) for symbol in request.symbols))
        results = []
        deadline_token = single_batch_deadline.set(
            monotonic() + config.upstream_timeout_seconds
        )
        try:
            for symbol in normalized:
                if monotonic() >= single_batch_deadline.get():
                    break
                cache_key = f"{request.source.value}:{symbol}"
                with single_caches_lock:
                    if cache_key not in single_caches:
                        def loader(selected=request.source, instrument=symbol):
                            source_id = f"SINGLE_{selected.value}"
                            quote_at = now()
                            quote = source_health.observe(
                                source_id,
                                lambda: build_single_quote(
                                    fetch_single_frame(selected, instrument),
                                    instrument,
                                    quote_at,
                                    f"AKSHARE_{selected.value}_SINGLE",
                                ),
                            )
                            succeeded_at = now()
                            persistent_single_cache.save(
                                selected.value, instrument, quote, succeeded_at
                            )
                            return {instrument: quote}

                        single_caches[cache_key] = SnapshotCache(
                            loader, ttl_seconds=1, max_stale_seconds=3
                        )
                        while len(single_caches) > config.single_cache_max_entries:
                            single_caches.popitem(last=False)
                    else:
                        single_caches.move_to_end(cache_key)
                    quote_cache = single_caches[cache_key]
                try:
                    snapshot = quote_cache.get()
                except UpstreamUnavailable:
                    log.warning(
                        "AKShare 单股行情部分失败：source=%s,symbol=%s",
                        request.source.value,
                        symbol,
                    )
                    stored = persistent_single_cache.load(request.source.value, symbol)
                    if stored is None:
                        continue
                    quote = dict(stored.quote)
                    quote["stale"] = True
                    quote["lastSuccessAt"] = stored.last_success_at
                else:
                    quote = dict(snapshot.quotes[symbol])
                    quote["stale"] = snapshot.stale
                    stored = persistent_single_cache.load(request.source.value, symbol)
                    quote["lastSuccessAt"] = (
                        stored.last_success_at if stored is not None else snapshot.fetched_at
                    )
                results.append(quote)
        finally:
            single_batch_deadline.reset(deadline_token)
        if not results:
            raise HTTPException(status_code=503, detail="所选单股行情源暂无可用数据")
        return results

    @application.post("/v1/quotes/us-positions", dependencies=[Depends(authorize)])
    def us_position_quotes(request: UsPositionQuoteRequest = Body()) -> list[dict]:
        try:
            symbols = validate_us_instruments(request.symbols)
        except ValueError as exception:
            raise HTTPException(status_code=422, detail=str(exception)) from exception
        source_id = "US_STOCK:POSITION:SINA"
        try:
            quotes = source_health.observe(source_id, lambda: fetch_us_quotes(symbols))
        except Exception as exception:
            log.warning("新浪美股持仓行情调用失败：count=%s,error=%s",
                        len(symbols), type(exception).__name__)
            raise HTTPException(status_code=503, detail="新浪美股持仓行情暂不可用") from exception
        if not quotes:
            raise HTTPException(status_code=503, detail="新浪美股持仓行情暂无可用数据")
        return quotes

    @application.post("/v1/funds/unit-nav", dependencies=[Depends(authorize)])
    def fund_unit_nav(request: FundNavRequest = Body()) -> list[dict]:
        symbols = []
        for raw in request.symbols:
            symbol = raw.strip().upper()
            if not re.fullmatch(r"CN_FUND:\d{6}", symbol):
                raise HTTPException(status_code=422, detail="公募基金标识格式无效")
            if symbol not in symbols:
                symbols.append(symbol)

        def fetch() -> list[dict]:
            current = normalize_current_unit_nav(fetch_fund_nav(), now())
            result = []
            for symbol in symbols:
                quote = current.get(symbol)
                if quote is None:
                    code = symbol.split(":", 1)[1]
                    quote = normalize_fund_history(
                        fetch_fund_history(code), code, code, now())
                if quote is not None:
                    result.append(quote)
            if not result:
                raise InvalidMarketData("所选基金暂无单位净值")
            return result

        try:
            return source_health.observe("PUBLIC_FUND:UNIT_NAV:EASTMONEY", fetch)
        except Exception as exception:
            log.warning("开放式基金单位净值调用失败：error=%s", type(exception).__name__)
            raise HTTPException(status_code=503, detail="开放式基金单位净值暂不可用") from exception

    @application.get("/v1/funds/unit-nav", dependencies=[Depends(authorize)])
    def all_fund_unit_nav() -> list[dict]:
        try:
            return source_health.observe(
                "PUBLIC_FUND:UNIT_NAV:EASTMONEY",
                lambda: list(normalize_current_unit_nav(fetch_fund_nav(), now()).values()),
            )
        except Exception as exception:
            log.warning("开放式基金全量单位净值调用失败：error=%s", type(exception).__name__)
            raise HTTPException(status_code=503, detail="开放式基金单位净值暂不可用") from exception

    @application.get("/v1/sources/status", dependencies=[Depends(authorize)])
    def sources_status() -> list[dict]:
        return source_health.all()

    @application.get("/v1/sources/capabilities", dependencies=[Depends(authorize)])
    def source_capabilities() -> list[dict]:
        health_by_id = {item["source"]: item for item in source_health.all()}
        return [
            {
                "market": item.market,
                "capability": item.capability,
                "source": item.source,
                "sourceId": item.source_id,
                "delayed": item.delayed,
                "health": health_by_id[item.source_id]["status"],
            }
            for item in SOURCE_CAPABILITIES
        ]

    @application.get("/v1/calendars/sessions", dependencies=[Depends(authorize)])
    def calendar_sessions(
        start: date = Query(),
        end: date = Query(),
    ) -> list[dict]:
        if end < start or end - start > timedelta(days=500):
            raise HTTPException(status_code=422, detail="交易日历日期范围无效")
        try:
            return [session.to_dict() for session in calendars.sessions(start, end)]
        except Exception as exception:
            log.error("交易日历生成失败：error=%s", type(exception).__name__)
            raise HTTPException(status_code=503, detail="交易日历暂不可用") from exception

    @application.get("/v1/calendars/a-share-check", dependencies=[Depends(authorize)])
    def a_share_calendar_check(start: date = Query(), end: date = Query()) -> dict:
        if end < start or end - start > timedelta(days=500):
            raise HTTPException(status_code=422, detail="交易日历日期范围无效")
        try:
            def check() -> dict[str, list[str]]:
                frame = fetch_a_share_calendar()
                if "trade_date" not in frame.columns:
                    raise InvalidMarketData("AKShare A 股交易日历缺少 trade_date 列")
                dates = {
                    pd.Timestamp(value).date() for value in frame["trade_date"].dropna().tolist()
                }
                return calendars.cross_check_a_share(start, end, dates)

            result = source_health.observe("CALENDAR_A_SHARE_CHECK", check)
        except Exception as exception:
            log.warning("A 股交易日历交叉检查失败：error=%s", type(exception).__name__)
            raise HTTPException(status_code=503, detail="A 股交易日历交叉检查暂不可用") from exception
        return {
            **result,
            "status": "MATCH" if not any(result.values()) else "MISMATCH",
            "source": "AKSHARE_SINA_TRADE_DATE",
            "checkedAt": now().isoformat(),
        }

    @application.get("/v1/snapshots", dependencies=[Depends(authorize)])
    def snapshots(symbols: str = Query(min_length=1, max_length=2048)) -> list[dict]:
        requested = symbols.split(",")
        if len(requested) > 50 or any(not symbol.strip() for symbol in requested):
            raise HTTPException(status_code=422, detail="证券数量或格式无效")
        normalized = []
        for raw_symbol in requested:
            normalized.append(normalize_symbol(raw_symbol))

        snapshot = current_snapshot()
        missing = [symbol for symbol in normalized if symbol not in snapshot.quotes]
        if missing:
            raise HTTPException(status_code=404, detail="部分证券当前没有有效行情")

        received_at = now()
        result = []
        for symbol in normalized:
            quote = dict(snapshot.quotes[symbol])
            quote["receivedAt"] = received_at
            quote["stale"] = snapshot.stale
            result.append(quote)
        return result

    return application
