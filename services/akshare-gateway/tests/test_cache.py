from datetime import datetime, timedelta, timezone
from threading import Barrier, Lock, Thread
from time import sleep

import pytest

from akshare_gateway.cache import SnapshotCache, UpstreamUnavailable
from akshare_gateway.single_quote_cache import RedisSingleQuoteCache


class FakeRedis:
    def __init__(self) -> None:
        self.hashes: dict[str, dict[str, str]] = {}
        self.expirations: dict[str, int] = {}

    def hset(self, name: str, key: str, value: str) -> int:
        bucket = self.hashes.setdefault(name, {})
        created = key not in bucket
        bucket[key] = value
        return int(created)

    def hget(self, name: str, key: str) -> str | None:
        return self.hashes.get(name, {}).get(key)

    def persist(self, name: str) -> bool:
        return self.expirations.pop(name, None) is not None

    def ttl(self, name: str) -> int:
        if name not in self.hashes:
            return -2
        return self.expirations.get(name, -1)


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


def test_single_quote_cache_isolated_by_source_and_has_no_ttl() -> None:
    redis = FakeRedis()
    redis.expirations["trading:quotes:akshare:single:EASTMONEY"] = 30
    cache = RedisSingleQuoteCache(redis)
    succeeded_at = datetime(2026, 7, 20, 1, 31, tzinfo=timezone.utc)

    cache.save(
        "EASTMONEY", "SSE:600519",
        {"instrumentId": "SSE:600519", "last": 1450, "source": "AKSHARE_EASTMONEY_SINGLE"},
        succeeded_at,
    )
    cache.save(
        "XUEQIU", "SSE:600519",
        {"instrumentId": "SSE:600519", "last": 1451, "source": "AKSHARE_XUEQIU_SINGLE"},
        succeeded_at,
    )

    assert cache.load("EASTMONEY", "SSE:600519").quote["last"] == 1450
    assert cache.load("XUEQIU", "SSE:600519").quote["last"] == 1451
    assert redis.ttl("trading:quotes:akshare:single:EASTMONEY") == -1
    assert redis.ttl("trading:quotes:akshare:single:XUEQIU") == -1


def test_single_quote_cache_success_overwrites_only_selected_security() -> None:
    redis = FakeRedis()
    cache = RedisSingleQuoteCache(redis)
    first_at = datetime(2026, 7, 20, 1, 31, tzinfo=timezone.utc)
    second_at = first_at + timedelta(seconds=10)
    cache.save("EASTMONEY", "SSE:600519", {"last": 1450}, first_at)
    cache.save("EASTMONEY", "SZSE:000001", {"last": 11.2}, first_at)

    cache.save("EASTMONEY", "SSE:600519", {"last": 1452}, second_at)

    updated = cache.load("EASTMONEY", "SSE:600519")
    untouched = cache.load("EASTMONEY", "SZSE:000001")
    assert updated.quote["last"] == 1452
    assert updated.last_success_at == second_at
    assert untouched.quote["last"] == 11.2
    assert untouched.last_success_at == first_at


def test_single_quote_cache_can_be_read_by_a_new_instance() -> None:
    redis = FakeRedis()
    succeeded_at = datetime(2026, 7, 20, 1, 31, tzinfo=timezone.utc)
    RedisSingleQuoteCache(redis).save(
        "XUEQIU", "SSE:600519",
        {"instrumentId": "SSE:600519", "last": 1450}, succeeded_at,
    )

    restored = RedisSingleQuoteCache(redis).load("XUEQIU", "SSE:600519")

    assert restored is not None
    assert restored.quote["last"] == 1450
    assert restored.last_success_at == succeeded_at


def test_single_quote_cache_returns_none_when_never_populated() -> None:
    assert RedisSingleQuoteCache(FakeRedis()).load("XUEQIU", "SSE:600519") is None
