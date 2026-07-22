from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from enum import Enum
from threading import Lock
from time import perf_counter
from typing import Callable, TypeVar


class MarketSource(str, Enum):
    EASTMONEY = "EASTMONEY"
    SINA = "SINA"


class SingleSource(str, Enum):
    EASTMONEY = "EASTMONEY"
    XUEQIU = "XUEQIU"


@dataclass
class SourceHealth:
    source: str
    status: str = "UNKNOWN"
    lastAttemptAt: datetime | None = None
    lastSuccessAt: datetime | None = None
    latencyMillis: int | None = None
    errorType: str | None = None


T = TypeVar("T")


class SourceHealthRegistry:
    """只记录来源级结果，不记录股票代码、用户或异常详情。"""

    def __init__(self) -> None:
        self._lock = Lock()
        self._states: dict[str, SourceHealth] = {}

    def observe(self, source: str, operation: Callable[[], T]) -> T:
        started = perf_counter()
        attempted_at = datetime.now(timezone.utc)
        try:
            result = operation()
        except Exception as exception:
            with self._lock:
                previous = self._states.get(source, SourceHealth(source=source))
                self._states[source] = SourceHealth(
                    source=source,
                    status="DOWN",
                    lastAttemptAt=attempted_at,
                    lastSuccessAt=previous.lastSuccessAt,
                    latencyMillis=round((perf_counter() - started) * 1000),
                    errorType=type(exception).__name__,
                )
            raise
        with self._lock:
            self._states[source] = SourceHealth(
                source=source,
                status="UP",
                lastAttemptAt=attempted_at,
                lastSuccessAt=datetime.now(timezone.utc),
                latencyMillis=round((perf_counter() - started) * 1000),
            )
        return result

    def all(self) -> list[dict]:
        with self._lock:
            configured = [
                *(f"SNAPSHOT_{source.value}" for source in MarketSource),
                *(f"SINGLE_{source.value}" for source in SingleSource),
            ]
            return [asdict(self._states.get(name, SourceHealth(source=name))) for name in configured]
