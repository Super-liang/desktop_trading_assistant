package com.tradingassistant.marketdata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingassistant.quote.InstrumentId;
import com.tradingassistant.quote.Quote;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RedisMarketSnapshotRepository {
    private static final String PREFIX = "trading:quotes:akshare:snapshot:";
    private static final String REFRESH_LOCK = PREFIX + "refresh-lock";
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final long maxStaleSeconds;

    public RedisMarketSnapshotRepository(StringRedisTemplate redis, ObjectMapper objectMapper,
            @Value("${app.market-data.snapshot-max-stale-seconds:86400}") long maxStaleSeconds) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.maxStaleSeconds = maxStaleSeconds;
    }

    public void replace(MarketDataConfig.SnapshotSource source, List<Quote> quotes) {
        if (quotes == null || quotes.isEmpty()) {
            throw new IllegalArgumentException("全市场快照不能为空");
        }
        String key = dataKey(source);
        String temporary = key + ":tmp:" + UUID.randomUUID();
        Map<String, String> values = new LinkedHashMap<>();
        for (Quote quote : quotes) {
            try {
                values.put(quote.instrumentId(), objectMapper.writeValueAsString(quote));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("行情序列化失败", exception);
            }
        }
        redis.opsForHash().putAll(temporary, values);
        redis.expire(temporary, Duration.ofSeconds(maxStaleSeconds));
        redis.rename(temporary, key);
        SnapshotMetadata metadata = new SnapshotMetadata(source.name(), Instant.now(), quotes.size());
        try {
            redis.opsForValue().set(metaKey(source), objectMapper.writeValueAsString(metadata),
                    Duration.ofSeconds(maxStaleSeconds));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("快照元数据序列化失败", exception);
        }
    }

    public List<Quote> find(MarketDataConfig.SnapshotSource source,
            List<InstrumentId> instruments) {
        List<Object> fields = instruments.stream().map(InstrumentId::canonical)
                .map(value -> (Object) value).toList();
        List<Object> raw = redis.opsForHash().multiGet(dataKey(source), fields);
        if (raw == null || raw.size() != instruments.size()) {
            throw new IllegalStateException("Redis 行情快照尚未就绪");
        }
        List<Quote> result = new ArrayList<>();
        for (Object value : raw) {
            if (value == null) throw new IllegalStateException("部分证券没有缓存行情");
            try {
                result.add(objectMapper.readValue(value.toString(), Quote.class));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Redis 行情内容无效", exception);
            }
        }
        return result;
    }

    public List<QuoteProviderSearchView> search(MarketDataConfig.SnapshotSource source,
            String query, int limit) {
        String keyword = query == null ? "" : query.strip().toUpperCase(Locale.ROOT);
        Map<Object, Object> entries = redis.opsForHash().entries(dataKey(source));
        List<QuoteProviderSearchView> result = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            try {
                Quote quote = objectMapper.readValue(entry.getValue().toString(), Quote.class);
                String code = quote.instrumentId().split(":", 2)[1];
                if (!code.contains(keyword) && !quote.name().toUpperCase(Locale.ROOT).contains(keyword)) {
                    continue;
                }
                result.add(new QuoteProviderSearchView(quote.instrumentId(), code, quote.name()));
                if (result.size() >= limit) break;
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Redis 行情内容无效", exception);
            }
        }
        return result;
    }

    public Optional<SnapshotMetadata> metadata(MarketDataConfig.SnapshotSource source) {
        String raw = redis.opsForValue().get(metaKey(source));
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(raw, SnapshotMetadata.class));
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    public Optional<String> acquireRefreshLock(int refreshSeconds) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(
                REFRESH_LOCK, token,
                // 覆盖慢请求和多实例时钟偏差，避免一次刷新未结束时另一实例重复抓取。
                Duration.ofSeconds(Math.max(60, refreshSeconds * 2L)));
        return Boolean.TRUE.equals(acquired) ? Optional.of(token) : Optional.empty();
    }

    public void releaseRefreshLock(String token) {
        redis.execute(RELEASE_LOCK, List.of(REFRESH_LOCK), token);
    }

    public boolean ping() {
        try (RedisConnection connection = Objects.requireNonNull(redis.getConnectionFactory())
                .getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping());
        }
    }

    private String dataKey(MarketDataConfig.SnapshotSource source) {
        return PREFIX + source.name().toLowerCase(Locale.ROOT);
    }

    private String metaKey(MarketDataConfig.SnapshotSource source) {
        return dataKey(source) + ":meta";
    }

    public record SnapshotMetadata(String source, Instant fetchedAt, int quoteCount) {}
    public record QuoteProviderSearchView(String instrumentId, String code, String name) {}
}
