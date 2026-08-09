package com.tradingassistant.catalog;

import com.tradingassistant.marketdata.AkshareGatewayClient;
import com.tradingassistant.market.InstrumentKey;
import com.tradingassistant.market.Market;
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
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.quotes.http.enabled", havingValue = "true")
public class SecurityCatalogSyncService {
    private static final Logger log = LoggerFactory.getLogger(SecurityCatalogSyncService.class);
    private static final String SYNC_LOCK_PREFIX = "trading:catalog:akshare:sync-lock:";
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

    @Transactional
    public boolean sync(Market market) {
        String token = UUID.randomUUID().toString();
        String lockKey = SYNC_LOCK_PREFIX + market.name();
        // 美股新浪降级目录约需数分钟分页拉取，锁期限必须覆盖网关目录总超时。
        Boolean acquired = redis.opsForValue().setIfAbsent(lockKey, token, Duration.ofMinutes(20));
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("证券目录同步已由其他实例执行：market={}", market);
            return false;
        }
        try {
            List<AkshareGatewayClient.CatalogInstrument> incoming = gateway.catalog(market);
            if (incoming == null || incoming.isEmpty()) {
                throw new IllegalStateException("AKShare 证券目录为空: " + market);
            }
            Instant syncedAt = Instant.now();
            Map<String, CatalogRow> validated = new LinkedHashMap<>();
            for (AkshareGatewayClient.CatalogInstrument row : incoming) {
                InstrumentKey instrument = new InstrumentKey(row.exchange(), row.code());
                if (!instrument.canonical().equals(row.instrumentId()) || row.market() != market
                        || row.currency() != market.currency()) {
                    throw new IllegalArgumentException("AKShare 证券目录字段不一致");
                }
                String name = row.name() == null ? "" : row.name().strip();
                if (name.isEmpty()) throw new IllegalArgumentException("AKShare 证券名称为空");
                if (validated.put(instrument.canonical(), new CatalogRow(row, name)) != null) {
                    throw new IllegalArgumentException("AKShare 证券目录包含重复代码");
                }
            }
            List<SecurityCatalogItem> existingItems = repository.findAllByMarket(market);
            int safeMinimum = Math.max(minimumCatalogSize,
                    (int) Math.ceil(existingItems.size() * 0.8));
            if (validated.size() < safeMinimum) {
                throw new IllegalStateException("AKShare " + market + "证券目录数量低于安全阈值");
            }
            Map<String, SecurityCatalogItem> existing = existingItems.stream()
                    .collect(java.util.stream.Collectors.toMap(
                            SecurityCatalogItem::getInstrumentId, item -> item));
            List<SecurityCatalogItem> updates = new ArrayList<>(validated.size());
            for (CatalogRow row : validated.values()) {
                AkshareGatewayClient.CatalogInstrument incomingRow = row.instrument();
                SecurityCatalogItem item = existing.get(incomingRow.instrumentId());
                if (item == null) {
                    item = new SecurityCatalogItem(incomingRow.instrumentId(), incomingRow.code(),
                            row.name(), incomingRow.market(), incomingRow.exchange(),
                            incomingRow.currency(), incomingRow.assetType(),
                            incomingRow.providerSymbol(), "AKSHARE", syncedAt);
                } else {
                    item.refresh(row.name(), "AKSHARE", syncedAt);
                }
                updates.add(item);
            }
            for (SecurityCatalogItem item : existingItems) {
                if (!validated.containsKey(item.getInstrumentId())) {
                    item.deactivate(syncedAt);
                    updates.add(item);
                }
            }
            repository.saveAll(updates);
            log.info("证券目录同步成功：market={},activeCount={},changedCount={}",
                    market, validated.size(), updates.size());
            return true;
        } catch (RuntimeException exception) {
            log.warn("证券目录同步失败，保留原目录：market={},error={}",
                    market, exception.getClass().getSimpleName());
            throw exception;
        } finally {
            redis.execute(RELEASE_LOCK, List.of(lockKey), token);
        }
    }

    public boolean sync() { return sync(Market.A_SHARE); }

    private record CatalogRow(AkshareGatewayClient.CatalogInstrument instrument, String name) {}
}
