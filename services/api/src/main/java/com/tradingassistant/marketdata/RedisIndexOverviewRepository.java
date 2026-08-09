package com.tradingassistant.marketdata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisIndexOverviewRepository {
    private static final String PREFIX = "trading:quotes:akshare:a_share:index:";
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RedisIndexOverviewRepository(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public synchronized void merge(MarketDataConfig.SnapshotSource source,
            List<MarketIndexQuote> incoming, Instant succeededAt) {
        Map<String, MarketIndexQuote> previous = new LinkedHashMap<>();
        find(source).forEach(item -> previous.put(item.instrumentId(), item));
        List<MarketIndexQuote> merged = new ArrayList<>();
        for (MarketIndexQuote current : incoming) {
            if (current.available() && current.price() != null) {
                merged.add(current.withState(true, succeededAt, false));
                continue;
            }
            MarketIndexQuote old = previous.get(current.instrumentId());
            merged.add(old == null ? current.withState(false, null, true)
                    : old.withState(false, old.lastSuccessAt(), true));
        }
        try {
            String key = key(source);
            redis.opsForValue().set(key, mapper.writeValueAsString(merged));
            redis.persist(key);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("指数行情序列化失败", exception);
        }
    }

    public List<MarketIndexQuote> find(MarketDataConfig.SnapshotSource source) {
        String raw = redis.opsForValue().get(key(source));
        if (raw == null) return List.of();
        redis.persist(key(source));
        try {
            return mapper.readValue(raw, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("指数行情缓存内容无效", exception);
        }
    }

    private String key(MarketDataConfig.SnapshotSource source) {
        return PREFIX + source.name().toLowerCase(Locale.ROOT);
    }
}
