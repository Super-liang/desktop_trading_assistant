from datetime import datetime, timezone
import os
from uuid import uuid4

import pytest

from akshare_gateway.single_quote_cache import RedisSingleQuoteCache


@pytest.mark.skipif(
    not os.getenv("AKSHARE_TEST_REDIS_URL"),
    reason="未配置 AKSHARE_TEST_REDIS_URL",
)
def test_real_redis_keeps_single_quote_without_expiry_across_clients() -> None:
    import redis

    redis_url = os.environ["AKSHARE_TEST_REDIS_URL"]
    source = f"TEST_{uuid4().hex}"
    key = f"{RedisSingleQuoteCache.KEY_PREFIX}:{source}"
    first_client = redis.Redis.from_url(redis_url, decode_responses=True)
    succeeded_at = datetime(2026, 7, 20, 1, 31, tzinfo=timezone.utc)
    try:
        RedisSingleQuoteCache(first_client).save(
            source,
            "SSE:600519",
            {
                "instrumentId": "SSE:600519",
                "last": 1450,
                "source": f"AKSHARE_{source}_SINGLE",
                "sourceTimestamp": succeeded_at,
                "receivedAt": succeeded_at,
            },
            succeeded_at,
        )

        assert first_client.ttl(key) == -1
        # 使用新连接模拟网关进程重启后的读取。
        second_client = redis.Redis.from_url(redis_url, decode_responses=True)
        restored = RedisSingleQuoteCache(second_client).load(source, "SSE:600519")
        assert restored is not None
        assert restored.quote["last"] == 1450
        assert restored.last_success_at == succeeded_at
    finally:
        first_client.delete(key)
