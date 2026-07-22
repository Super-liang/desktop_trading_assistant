package com.tradingassistant.catalog;

import com.tradingassistant.marketdata.AkshareGatewayClient;
import com.tradingassistant.quote.InstrumentId;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.quotes.http.enabled", havingValue = "true")
public class SecurityCatalogSyncService {
    private static final Logger log = LoggerFactory.getLogger(SecurityCatalogSyncService.class);
    private static final String SYNC_LOCK = "trading:catalog:akshare:sync-lock";
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) "
                    + "else return 0 end", Long.class);
    private final SecurityCatalogRepository repository;
    private final AkshareGatewayClient gateway;
    private final StringRedisTemplate redis;
    private final int minimumCatalogSize;

    public SecurityCatalogSyncService(SecurityCatalogRepository repository,
            AkshareGatewayClient gateway, StringRedisTemplate redis,
            @Value("${app.market-data.catalog-min-size:1000}") int minimumCatalogSize) {
        this.repository = repository;
        this.gateway = gateway;
        this.redis = redis;
        this.minimumCatalogSize = minimumCatalogSize;
    }

    public void sync() {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(SYNC_LOCK, token, Duration.ofMinutes(10));
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("A 股证券目录同步已由其他实例执行");
            return;
        }
        try {
            List<AkshareGatewayClient.CatalogInstrument> incoming = gateway.catalog();
            if (incoming == null || incoming.isEmpty()) {
                throw new IllegalStateException("AKShare A 股证券目录为空");
            }
            Instant syncedAt = Instant.now();
            Map<String, CatalogRow> validated = new LinkedHashMap<>();
            for (AkshareGatewayClient.CatalogInstrument row : incoming) {
                InstrumentId instrument = InstrumentId.parse(row.instrumentId());
                if (!instrument.code().equals(row.code()) || instrument.exchange() != row.exchange()
                        || instrument.assetType() != row.assetType()) {
                    throw new IllegalArgumentException("AKShare 证券目录字段不一致");
                }
                String name = row.name() == null ? "" : row.name().strip();
                if (name.isEmpty()) throw new IllegalArgumentException("AKShare 证券名称为空");
                if (validated.put(instrument.canonical(), new CatalogRow(instrument, name)) != null) {
                    throw new IllegalArgumentException("AKShare 证券目录包含重复代码");
                }
            }
            List<SecurityCatalogItem> existingItems = repository.findAll();
            int safeMinimum = Math.max(minimumCatalogSize,
                    (int) Math.ceil(existingItems.size() * 0.8));
            if (validated.size() < safeMinimum) {
                throw new IllegalStateException("AKShare A 股证券目录数量低于安全阈值");
            }
            Map<String, SecurityCatalogItem> existing = existingItems.stream()
                    .collect(java.util.stream.Collectors.toMap(
                            SecurityCatalogItem::getInstrumentId, item -> item));
            List<SecurityCatalogItem> updates = new ArrayList<>(validated.size());
            for (CatalogRow row : validated.values()) {
                SecurityCatalogItem item = existing.get(row.instrument().canonical());
                if (item == null) {
                    item = new SecurityCatalogItem(row.instrument(), row.name(), "AKSHARE", syncedAt);
                } else {
                    item.refresh(row.name(), "AKSHARE", syncedAt);
                }
                updates.add(item);
            }
            repository.saveAll(updates);
            log.info("A 股证券目录同步成功：count={}", updates.size());
        } catch (RuntimeException exception) {
            log.warn("A 股证券目录同步失败，保留原目录：error={}",
                    exception.getClass().getSimpleName());
            throw exception;
        } finally {
            redis.execute(RELEASE_LOCK, List.of(SYNC_LOCK), token);
        }
    }

    private record CatalogRow(InstrumentId instrument, String name) {}
}
