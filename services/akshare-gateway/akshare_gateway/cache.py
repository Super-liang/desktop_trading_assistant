from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from threading import Condition
from time import monotonic
from typing import Any, Callable, Protocol


class Clock(Protocol):
    def monotonic(self) -> float: ...
    def utcnow(self) -> datetime: ...


class SystemClock:
    def monotonic(self) -> float:
        return monotonic()

    def utcnow(self) -> datetime:
        return datetime.now(timezone.utc)


class UpstreamUnavailable(RuntimeError):
    """上游不可用且没有仍可展示的最后成功快照。"""


@dataclass(frozen=True)
class Snapshot:
    quotes: dict[str, dict[str, Any]]
    fetched_at: datetime
    stale: bool


class SnapshotCache:
    def __init__(
        self,
        loader: Callable[[], dict[str, dict[str, Any]]],
        ttl_seconds: float,
        max_stale_seconds: float,
        clock: Clock | None = None,
    ) -> None:
        if ttl_seconds <= 0 or max_stale_seconds < ttl_seconds:
            raise ValueError("缓存时间配置无效")
        self._loader = loader
        self._ttl_seconds = ttl_seconds
        self._max_stale_seconds = max_stale_seconds
        self._clock = clock or SystemClock()
        self._condition = Condition()
        self._quotes: dict[str, dict[str, Any]] | None = None
        self._fetched_at: datetime | None = None
        self._loaded_at_monotonic: float | None = None
        self._refreshing = False

    def get(self) -> Snapshot:
        with self._condition:
            while True:
                age = self._age()
                if self._quotes is not None and age <= self._ttl_seconds:
                    return self._snapshot(stale=False)
                if not self._refreshing:
                    self._refreshing = True
                    break
                self._condition.wait()

        try:
            quotes = self._loader()
            if not quotes:
                raise RuntimeError("上游返回空行情")
        except Exception as exception:
            with self._condition:
                self._refreshing = False
                self._condition.notify_all()
                if self._quotes is not None and self._age() <= self._max_stale_seconds:
                    return self._snapshot(stale=True)
            raise UpstreamUnavailable("AKShare 上游行情暂不可用") from exception

        with self._condition:
            self._quotes = quotes
            self._fetched_at = self._clock.utcnow()
            self._loaded_at_monotonic = self._clock.monotonic()
            self._refreshing = False
            self._condition.notify_all()
            return self._snapshot(stale=False)

    def status(self) -> dict[str, Any]:
        with self._condition:
            age = self._age()
            return {
                "loaded": self._quotes is not None,
                "refreshing": self._refreshing,
                "ageSeconds": None if age == float("inf") else round(age, 3),
                "stale": self._quotes is not None and age > self._ttl_seconds,
            }

    def _age(self) -> float:
        if self._loaded_at_monotonic is None:
            return float("inf")
        return max(0.0, self._clock.monotonic() - self._loaded_at_monotonic)

    def _snapshot(self, stale: bool) -> Snapshot:
        assert self._quotes is not None
        assert self._fetched_at is not None
        return Snapshot(self._quotes, self._fetched_at, stale)
