from __future__ import annotations

import hmac
import logging
import os
import re
from collections import OrderedDict
from contextvars import ContextVar
from dataclasses import dataclass
from datetime import datetime, timezone
from threading import Lock
from time import monotonic
from typing import Callable

import pandas as pd
from fastapi import Body, Depends, FastAPI, Header, HTTPException, Query
from pydantic import BaseModel, Field

from .cache import SnapshotCache, UpstreamUnavailable
from .http_timeout import install_default_requests_timeout
from .isolation import run_isolated
from .market import (
    InvalidMarketData,
    asset_type_for,
    build_market_snapshot,
    build_single_quote,
    exchange_for,
    normalize_code,
)
from .sources import MarketSource, SingleSource, SourceHealthRegistry

SYMBOL_PATTERN = re.compile(r"^(SSE|SZSE|BSE):(\d{6})$")
log = logging.getLogger(__name__)


@dataclass(frozen=True)
class Settings:
    api_key: str
    cache_ttl_seconds: float = 10
    max_stale_seconds: float = 30
    search_limit: int = 20
    upstream_timeout_seconds: float = 30
    single_cache_max_entries: int = 1000

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
            single_cache_max_entries=int(
                os.getenv("AKSHARE_SINGLE_CACHE_MAX_ENTRIES", "1000")
            ),
        )

    def validate(self) -> None:
        if not self.api_key.strip():
            raise RuntimeError("必须配置 AKSHARE_API_KEY")
        if not 1 <= self.search_limit <= 100:
            raise RuntimeError("AKSHARE_SEARCH_LIMIT 必须在 1 到 100 之间")
        if not 1 <= self.upstream_timeout_seconds <= 120:
            raise RuntimeError("AKSHARE_UPSTREAM_TIMEOUT_SECONDS 必须在 1 到 120 之间")
        if not 50 <= self.single_cache_max_entries <= 10000:
            raise RuntimeError("AKSHARE_SINGLE_CACHE_MAX_ENTRIES 必须在 50 到 10000 之间")


def load_akshare_frame() -> pd.DataFrame:
    # 延迟导入避免健康检查和单元测试触发上游 SDK 初始化。
    import akshare as ak

    try:
        return ak.stock_zh_a_spot_em()
    except Exception as exception:
        # 东方财富公开接口可能因网络或限流失败，使用 AKShare 的新浪全市场接口兜底。
        log.warning(
            "AKShare 东方财富接口失败，切换新浪接口：error=%s",
            type(exception).__name__,
        )
        return ak.stock_zh_a_spot()


def load_market_frame(source: MarketSource) -> pd.DataFrame:
    import akshare as ak

    if source == MarketSource.EASTMONEY:
        return ak.stock_zh_a_spot_em()
    return ak.stock_zh_a_spot()


def load_single_frame(source: SingleSource, instrument_id: str) -> pd.DataFrame:
    import akshare as ak

    exchange, code = instrument_id.split(":", 1)
    if source == SingleSource.EASTMONEY:
        if exchange == "BSE":
            raise ValueError("东财 stock_bid_ask_em 不支持北交所 market id")
        return ak.stock_bid_ask_em(symbol=code)
    prefix = {"SSE": "SH", "SZSE": "SZ", "BSE": "BJ"}[exchange]
    return ak.stock_individual_spot_xq(symbol=f"{prefix}{code}")


def load_instrument_frame() -> pd.DataFrame:
    import akshare as ak

    return ak.stock_info_a_code_name()


class SingleQuoteRequest(BaseModel):
    source: SingleSource
    symbols: list[str] = Field(min_length=1, max_length=50)


def create_app(
    settings: Settings | None = None,
    frame_loader: Callable[[], pd.DataFrame] | None = None,
    utcnow: Callable[[], datetime] | None = None,
    market_frame_loader: Callable[[MarketSource], pd.DataFrame] | None = None,
    single_frame_loader: Callable[[SingleSource, str], pd.DataFrame] | None = None,
    instrument_frame_loader: Callable[[], pd.DataFrame] | None = None,
) -> FastAPI:
    config = settings or Settings.from_env()
    config.validate()
    install_default_requests_timeout(config.upstream_timeout_seconds)
    load_frame = frame_loader or (lambda: run_isolated(
        "legacy", timeout_seconds=config.upstream_timeout_seconds
    ))
    fetch_market_frame = market_frame_loader or (lambda source: run_isolated(
        "market", source=source.value, timeout_seconds=config.upstream_timeout_seconds
    ))
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
    fetch_instrument_frame = instrument_frame_loader or (lambda: run_isolated(
        "instruments", timeout_seconds=config.upstream_timeout_seconds
    ))
    now = utcnow or (lambda: datetime.now(timezone.utc))
    source_health = SourceHealthRegistry()
    single_caches: OrderedDict[str, SnapshotCache] = OrderedDict()
    single_caches_lock = Lock()

    def load_quotes():
        return build_market_snapshot(load_frame(), now())

    cache = SnapshotCache(
        load_quotes,
        ttl_seconds=config.cache_ttl_seconds,
        max_stale_seconds=config.max_stale_seconds,
    )
    def load_instruments() -> dict[str, dict[str, str]]:
        instruments = {}
        for row in fetch_instrument_frame().to_dict(orient="records"):
            try:
                code = normalize_code(row["code"])
                name = str(row["name"]).strip()
                if not name:
                    continue
                instruments[f"{exchange_for(code)}:{code}"] = {"code": code, "name": name}
            except (InvalidMarketData, KeyError):
                continue
        if not instruments:
            raise InvalidMarketData("AKShare A 股证券目录没有可用数据")
        return instruments

    instrument_cache = SnapshotCache(
        load_instruments,
        ttl_seconds=3600,
        max_stale_seconds=86400,
    )
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
    def instrument_search(query: str = Query(min_length=1, max_length=64)) -> list[dict]:
        keyword = query.strip().upper()
        try:
            directory = instrument_cache.get().quotes
        except UpstreamUnavailable as exception:
            raise HTTPException(status_code=503, detail="A 股证券目录暂不可用") from exception
        results = []
        for instrument_id, instrument in directory.items():
            if keyword not in instrument["code"] and keyword not in instrument["name"].upper():
                continue
            exchange = instrument_id.split(":", 1)[0]
            results.append({
                "instrumentId": instrument_id,
                "code": instrument["code"],
                "name": instrument["name"],
                "exchange": exchange,
                "assetType": asset_type_for(instrument["code"]),
            })
            if len(results) >= config.search_limit:
                break
        return results

    @application.get("/v1/market/snapshot", dependencies=[Depends(authorize)])
    def market_snapshot(source: MarketSource) -> list[dict]:
        source_id = f"SNAPSHOT_{source.value}"
        try:
            quotes = source_health.observe(
                source_id,
                lambda: build_market_snapshot(
                    fetch_market_frame(source),
                    now(),
                    f"AKSHARE_{source.value}_SNAPSHOT",
                ),
            )
        except Exception as exception:
            log.warning("AKShare 全市场来源调用失败：source=%s,error=%s", source.value,
                        type(exception).__name__)
            raise HTTPException(status_code=503, detail="所选全市场行情源暂不可用") from exception
        return list(quotes.values())

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
                    raise HTTPException(status_code=503, detail="单股行情批量查询超时")
                cache_key = f"{request.source.value}:{symbol}"
                with single_caches_lock:
                    if cache_key not in single_caches:
                        def loader(selected=request.source, instrument=symbol):
                            source_id = f"SINGLE_{selected.value}"
                            quote = source_health.observe(
                                source_id,
                                lambda: build_single_quote(
                                    fetch_single_frame(selected, instrument),
                                    instrument,
                                    now(),
                                    f"AKSHARE_{selected.value}_SINGLE",
                                ),
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
                except UpstreamUnavailable as exception:
                    raise HTTPException(status_code=503, detail="所选单股行情源暂不可用") from exception
                quote = dict(snapshot.quotes[symbol])
                quote["receivedAt"] = now()
                quote["stale"] = snapshot.stale
                results.append(quote)
        finally:
            single_batch_deadline.reset(deadline_token)
        return results

    @application.get("/v1/sources/status", dependencies=[Depends(authorize)])
    def sources_status() -> list[dict]:
        return source_health.all()

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
