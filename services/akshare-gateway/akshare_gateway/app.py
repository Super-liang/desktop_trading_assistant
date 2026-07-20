from __future__ import annotations

import hmac
import logging
import os
import re
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Callable

import pandas as pd
from fastapi import Depends, FastAPI, Header, HTTPException, Query

from .cache import SnapshotCache, UpstreamUnavailable
from .market import asset_type_for, build_market_snapshot, exchange_for

SYMBOL_PATTERN = re.compile(r"^(SSE|SZSE|BSE):(\d{6})$")
log = logging.getLogger(__name__)


@dataclass(frozen=True)
class Settings:
    api_key: str
    cache_ttl_seconds: float = 10
    max_stale_seconds: float = 30
    search_limit: int = 20

    @classmethod
    def from_env(cls) -> "Settings":
        return cls(
            api_key=os.getenv("AKSHARE_API_KEY", ""),
            cache_ttl_seconds=float(os.getenv("AKSHARE_CACHE_TTL_SECONDS", "10")),
            max_stale_seconds=float(os.getenv("AKSHARE_MAX_STALE_SECONDS", "30")),
            search_limit=int(os.getenv("AKSHARE_SEARCH_LIMIT", "20")),
        )

    def validate(self) -> None:
        if not self.api_key.strip():
            raise RuntimeError("必须配置 AKSHARE_API_KEY")
        if not 1 <= self.search_limit <= 100:
            raise RuntimeError("AKSHARE_SEARCH_LIMIT 必须在 1 到 100 之间")


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


def create_app(
    settings: Settings | None = None,
    frame_loader: Callable[[], pd.DataFrame] | None = None,
    utcnow: Callable[[], datetime] | None = None,
) -> FastAPI:
    config = settings or Settings.from_env()
    config.validate()
    load_frame = frame_loader or load_akshare_frame
    now = utcnow or (lambda: datetime.now(timezone.utc))

    def load_quotes():
        return build_market_snapshot(load_frame(), now())

    cache = SnapshotCache(
        load_quotes,
        ttl_seconds=config.cache_ttl_seconds,
        max_stale_seconds=config.max_stale_seconds,
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

    @application.get("/v1/snapshots", dependencies=[Depends(authorize)])
    def snapshots(symbols: str = Query(min_length=1, max_length=2048)) -> list[dict]:
        requested = symbols.split(",")
        if len(requested) > 50 or any(not symbol.strip() for symbol in requested):
            raise HTTPException(status_code=422, detail="证券数量或格式无效")
        normalized = []
        for raw_symbol in requested:
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
            normalized.append(symbol)

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
