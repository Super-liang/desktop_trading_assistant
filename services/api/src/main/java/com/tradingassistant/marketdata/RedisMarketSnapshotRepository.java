package com.tradingassistant.marketdata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingassistant.quote.InstrumentId;
import com.tradingassistant.quote.Quote;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Repository
public class RedisMarketSnapshotRepository {
    private static final Logger log = LoggerFactory.getLogger(RedisMarketSnapshotRepository.class);
    private static final String PREFIX = "trading:quotes:akshare:snapshot:";
    private static final String METADATA_FIELD = "__metadata__";
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);
    private static final DefaultRedisScript<Long> UPDATE_SNAPSHOT = new DefaultRedisScript<>(
            "redis.call('del', KEYS[2]); "
                    + "if redis.call('exists', KEYS[1]) == 1 then "
                    + "local old = redis.call('hgetall', KEYS[1]); "
                    + "for i = 1, #old, 2 do redis.call('hset', KEYS[2], old[i], old[i + 1]); end; end; "
                    + "for i = 2, #ARGV, 2 do redis.call('hset', KEYS[2], ARGV[i], ARGV[i + 1]); end; "
                    + "redis.call('hset', KEYS[2], '" + METADATA_FIELD + "', ARGV[1]); "
                    + "redis.call('persist', KEYS[2]); redis.call('rename', KEYS[2], KEYS[1]); "
                    + "return redis.call('hlen', KEYS[1]) - 1;",
            Long.class);
    private static final DefaultRedisScript<Long> MIGRATE_LEGACY_SNAPSHOT = new DefaultRedisScript<>(
            "if redis.call('exists', KEYS[1]) == 0 then redis.call('del', KEYS[2]); return 0; end; "
                    + "if redis.call('hexists', KEYS[1], '" + METADATA_FIELD + "') == 0 then "
                    + "redis.call('hset', KEYS[1], '" + METADATA_FIELD + "', ARGV[1]); end; "
                    + "redis.call('persist', KEYS[1]); redis.call('del', KEYS[2]); return 1;",
            Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisMarketSnapshotRepository(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void replace(MarketDataConfig.SnapshotSource source, List<Quote> quotes) {
        if (quotes == null || quotes.isEmpty()) {
            throw new IllegalArgumentException("全市场快照不能为空");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Quote quote : quotes) {
            try {
                values.put(quote.instrumentId(), objectMapper.writeValueAsString(quote));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("行情序列化失败", exception);
            }
        }
        SnapshotMetadata metadata = new SnapshotMetadata(source.name(), Instant.now(), quotes.size());
        try {
            List<String> arguments = new ArrayList<>();
            arguments.add(objectMapper.writeValueAsString(metadata));
            values.forEach((field, value) -> {
                arguments.add(field);
                arguments.add(value);
            });
            String temporary = dataKey(source) + ":tmp:" + UUID.randomUUID();
            try {
                redis.execute(UPDATE_SNAPSHOT, List.of(dataKey(source), temporary),
                        arguments.toArray());
                redis.delete(metaKey(source));
            } catch (RuntimeException exception) {
                redis.delete(temporary);
                throw exception;
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("快照元数据序列化失败", exception);
        }
    }

    public List<Quote> find(MarketDataConfig.SnapshotSource source,
            List<InstrumentId> instruments) {
        if (metadata(source).isEmpty()) {
            throw new IllegalStateException("Redis 行情快照尚未就绪");
        }
        List<Object> fields = instruments.stream().map(InstrumentId::canonical)
                .map(value -> (Object) value).toList();
        List<Object> raw = redis.opsForHash().multiGet(dataKey(source), fields);
        if (raw == null || raw.size() != instruments.size()) {
            throw new IllegalStateException("Redis 行情快照尚未就绪");
        }
        List<Quote> result = new ArrayList<>();
        for (Object value : raw) {
            if (value == null) continue;
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
            if (METADATA_FIELD.equals(entry.getKey().toString())) continue;
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
        String raw = (String) redis.opsForHash().get(dataKey(source), METADATA_FIELD);
        if (raw == null) raw = migrateLegacyMetadata(source);
        if (raw == null) return Optional.empty();
        redis.persist(dataKey(source));
        try {
            return Optional.of(objectMapper.readValue(raw, SnapshotMetadata.class));
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrateLegacySnapshots() {
        for (MarketDataConfig.SnapshotSource source : MarketDataConfig.SnapshotSource.values()) {
            try {
                metadata(source);
            } catch (RuntimeException exception) {
                log.warn("Redis 旧快照 TTL 迁移暂未完成：source={},error={}",
                        source, exception.getClass().getSimpleName());
            }
        }
    }

    private String migrateLegacyMetadata(MarketDataConfig.SnapshotSource source) {
        String legacy = redis.opsForValue().get(metaKey(source));
        if (legacy == null) legacy = rebuildMetadataFromQuotes(source);
        if (legacy == null) return null;
        Long migrated = redis.execute(MIGRATE_LEGACY_SNAPSHOT,
                List.of(dataKey(source), metaKey(source)), legacy);
        if (migrated == null || migrated == 0) return null;
        Object current = redis.opsForHash().get(dataKey(source), METADATA_FIELD);
        return current == null ? null : current.toString();
    }

    private String rebuildMetadataFromQuotes(MarketDataConfig.SnapshotSource source) {
        Map<Object, Object> entries = redis.opsForHash().entries(dataKey(source));
        Instant latest = null;
        int count = 0;
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            if (METADATA_FIELD.equals(entry.getKey().toString())) continue;
            try {
                Quote quote = objectMapper.readValue(entry.getValue().toString(), Quote.class);
                count++;
                if (quote.sourceTimestamp() != null
                        && (latest == null || quote.sourceTimestamp().isAfter(latest))) {
                    latest = quote.sourceTimestamp();
                }
            } catch (JsonProcessingException ignored) {
                // 无法恢复的损坏字段不参与元数据重建，正常查询仍会暴露其内容错误。
            }
        }
        if (count == 0 || latest == null) return null;
        try {
            return objectMapper.writeValueAsString(new SnapshotMetadata(source.name(), latest, count));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("快照元数据重建失败", exception);
        }
    }

    public Optional<String> acquireRefreshLock(MarketDataConfig.SnapshotSource source,
            int refreshSeconds) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(
                refreshLockKey(source), token,
                // 覆盖慢请求和多实例时钟偏差，避免一次刷新未结束时另一实例重复抓取。
                Duration.ofSeconds(Math.max(60, refreshSeconds * 2L)));
        return Boolean.TRUE.equals(acquired) ? Optional.of(token) : Optional.empty();
    }

    public void releaseRefreshLock(MarketDataConfig.SnapshotSource source, String token) {
        redis.execute(RELEASE_LOCK, List.of(refreshLockKey(source)), token);
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

    private String refreshLockKey(MarketDataConfig.SnapshotSource source) {
        return dataKey(source) + ":refresh-lock";
    }

    public record SnapshotMetadata(String source, Instant fetchedAt, int quoteCount) {}
    public record QuoteProviderSearchView(String instrumentId, String code, String name) {}
}
