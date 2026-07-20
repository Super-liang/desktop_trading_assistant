from datetime import datetime, timedelta, timezone
from threading import Barrier, Lock, Thread
from time import sleep

import pytest

from akshare_gateway.cache import SnapshotCache, UpstreamUnavailable


class ManualClock:
    def __init__(self) -> None:
        self.monotonic_value = 0.0
        self.utc_value = datetime(2026, 7, 20, tzinfo=timezone.utc)

    def monotonic(self) -> float:
        return self.monotonic_value

    def utcnow(self) -> datetime:
        return self.utc_value

    def advance(self, seconds: float) -> None:
        self.monotonic_value += seconds
        self.utc_value += timedelta(seconds=seconds)


def test_reuses_snapshot_within_ttl() -> None:
    clock = ManualClock()
    calls = 0

    def loader():
        nonlocal calls
        calls += 1
        return {"SSE:600519": {"last": 1450}}

    cache = SnapshotCache(loader, ttl_seconds=3, max_stale_seconds=30, clock=clock)

    assert cache.get().quotes["SSE:600519"]["last"] == 1450
    clock.advance(2)
    assert cache.get().quotes["SSE:600519"]["last"] == 1450
    assert calls == 1


def test_coalesces_concurrent_refreshes() -> None:
    clock = ManualClock()
    calls = 0
    calls_lock = Lock()
    barrier = Barrier(5)

    def loader():
        nonlocal calls
        with calls_lock:
            calls += 1
        sleep(0.05)
        return {"SSE:600519": {"last": 1450}}

    cache = SnapshotCache(loader, ttl_seconds=3, max_stale_seconds=30, clock=clock)
    results: list[float] = []

    def read() -> None:
        barrier.wait()
        results.append(cache.get().quotes["SSE:600519"]["last"])

    threads = [Thread(target=read) for _ in range(5)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()

    assert results == [1450] * 5
    assert calls == 1


def test_returns_stale_snapshot_only_within_max_stale_window() -> None:
    clock = ManualClock()
    failing = False

    def loader():
        if failing:
            raise RuntimeError("network down")
        return {"SSE:600519": {"last": 1450}}

    cache = SnapshotCache(loader, ttl_seconds=3, max_stale_seconds=30, clock=clock)
    cache.get()
    failing = True
    clock.advance(4)

    stale = cache.get()
    assert stale.stale is True
    assert stale.quotes["SSE:600519"]["last"] == 1450

    clock.advance(27)
    with pytest.raises(UpstreamUnavailable):
        cache.get()


def test_raises_when_first_load_fails() -> None:
    clock = ManualClock()
    cache = SnapshotCache(
        lambda: (_ for _ in ()).throw(RuntimeError("network down")),
        ttl_seconds=3,
        max_stale_seconds=30,
        clock=clock,
    )

    with pytest.raises(UpstreamUnavailable):
        cache.get()
