from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime
from typing import Any, Protocol


class RedisHashClient(Protocol):
    def hset(self, name: str, key: str, value: str) -> int: ...
    def hget(self, name: str, key: str) -> str | bytes | None: ...
    def persist(self, name: str) -> bool: ...


@dataclass(frozen=True)
class StoredSingleQuote:
    quote: dict[str, Any]
    last_success_at: datetime


class SingleQuoteCache(Protocol):
    def save(
        self,
        source: str,
        instrument_id: str,
        quote: dict[str, Any],
        last_success_at: datetime,
    ) -> None: ...

    def load(self, source: str, instrument_id: str) -> StoredSingleQuote | None: ...


class RedisSingleQuoteCache:
    """按来源隔离、永不过期的单股最后成功行情。"""

    KEY_PREFIX = "trading:quotes:akshare:single"

    def __init__(self, redis_client: RedisHashClient) -> None:
        self._redis = redis_client

    @classmethod
    def from_url(cls, redis_url: str) -> "RedisSingleQuoteCache":
        import redis

        return cls(redis.Redis.from_url(redis_url, decode_responses=True))

    def save(
        self,
        source: str,
        instrument_id: str,
        quote: dict[str, Any],
        last_success_at: datetime,
    ) -> None:
        if last_success_at.tzinfo is None:
            raise ValueError("last_success_at 必须包含时区")
        key = self._key(source)
        value = json.dumps(
            {
                "quote": quote,
                "lastSuccessAt": last_success_at.isoformat(),
            },
            ensure_ascii=False,
            separators=(",", ":"),
            default=self._json_default,
        )
        self._redis.hset(key, instrument_id, value)
        # HSET 不会移除 Hash 已有的过期时间；显式 PERSIST 保证最后值永久保留。
        self._redis.persist(key)

    def load(self, source: str, instrument_id: str) -> StoredSingleQuote | None:
        raw = self._redis.hget(self._key(source), instrument_id)
        if raw is None:
            return None
        if isinstance(raw, bytes):
            raw = raw.decode("utf-8")
        document = json.loads(raw)
        quote = dict(document["quote"])
        for field in ("sourceTimestamp", "receivedAt"):
            if isinstance(quote.get(field), str):
                quote[field] = datetime.fromisoformat(quote[field])
        return StoredSingleQuote(
            quote=quote,
            last_success_at=datetime.fromisoformat(document["lastSuccessAt"]),
        )

    @classmethod
    def _key(cls, source: str) -> str:
        return f"{cls.KEY_PREFIX}:{source}"

    @staticmethod
    def _json_default(value: Any) -> str:
        if isinstance(value, datetime):
            return value.isoformat()
        raise TypeError(f"无法序列化缓存字段: {type(value).__name__}")
