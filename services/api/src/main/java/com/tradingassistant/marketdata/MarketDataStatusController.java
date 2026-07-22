package com.tradingassistant.marketdata;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "app.quotes.http.enabled", havingValue = "true")
public class MarketDataStatusController {
    private final MarketDataConfigService configs;
    private final RedisMarketSnapshotRepository snapshots;
    private final AkshareGatewayClient gateway;

    public MarketDataStatusController(MarketDataConfigService configs,
            RedisMarketSnapshotRepository snapshots, AkshareGatewayClient gateway) {
        this.configs = configs;
        this.snapshots = snapshots;
        this.gateway = gateway;
    }

    @GetMapping("/api/v1/market-data/status")
    public StatusView status() {
        MarketDataConfig config = configs.current();
        List<ComponentStatus> components = new ArrayList<>();
        try {
            AkshareGatewayClient.GatewayHealth health = gateway.health();
            components.add(new ComponentStatus("AKSHARE_GATEWAY", "AKShare 网关",
                    "UP".equals(health.status()) ? "UP" : "DEGRADED", null, null,
                    health.status()));
        } catch (RuntimeException exception) {
            components.add(new ComponentStatus("AKSHARE_GATEWAY", "AKShare 网关", "DOWN",
                    null, null, exception.getClass().getSimpleName()));
        }

        if (config.getMode() == MarketDataConfig.Mode.SINGLE_STOCK) {
            components.add(new ComponentStatus("REDIS_SNAPSHOT", "Redis 快照",
                    "NOT_APPLICABLE", null, null, "单股模式不使用全市场缓存"));
        } else {
            try {
                boolean ping = snapshots.ping();
                var metadata = snapshots.metadata(config.getSnapshotSource());
                if (!ping) throw new IllegalStateException("Redis 未响应 PONG");
                if (metadata.isEmpty()) {
                    components.add(new ComponentStatus("REDIS_SNAPSHOT", "Redis 快照",
                            "UNKNOWN", null, null, "等待首次快照"));
                } else {
                    Instant now = Instant.now();
                    long age = Math.max(0, Duration.between(metadata.get().fetchedAt(), now).toSeconds());
                    boolean overdueDuringTrading = MarketSnapshotScheduler.isTradingTime(now)
                            && age > config.getRefreshSeconds() * 2L;
                    String state = overdueDuringTrading ? "DEGRADED" : "UP";
                    components.add(new ComponentStatus("REDIS_SNAPSHOT", "Redis 快照", state,
                            metadata.get().fetchedAt(), age, metadata.get().quoteCount() + " 只证券"));
                }
            } catch (RuntimeException exception) {
                components.add(new ComponentStatus("REDIS_SNAPSHOT", "Redis 快照", "DOWN",
                        null, null, exception.getClass().getSimpleName()));
            }
        }

        String sourceId = config.getMode() == MarketDataConfig.Mode.MARKET_SNAPSHOT
                ? "SNAPSHOT_" + config.getSnapshotSource().name()
                : "SINGLE_" + config.getSingleSource().name();
        try {
            AkshareGatewayClient.SourceHealth selected = gateway.sourceStatus().stream()
                    .filter(item -> sourceId.equals(item.source())).findFirst().orElse(null);
            components.add(selected == null
                    ? new ComponentStatus("UPSTREAM", sourceId, "UNKNOWN", null, null, "尚未调用")
                    : new ComponentStatus("UPSTREAM", sourceId, selected.status(),
                            selected.lastSuccessAt(), null,
                            selected.errorType() == null ? selected.latencyMillis() + " ms" : selected.errorType()));
        } catch (RuntimeException exception) {
            components.add(new ComponentStatus("UPSTREAM", sourceId, "UNKNOWN",
                    null, null, "网关状态不可用"));
        }
        return new StatusView(config.getMode(), components, Instant.now());
    }

    public record ComponentStatus(String id, String label, String status, Instant lastSuccessAt,
            Long ageSeconds, String detail) {}
    public record StatusView(MarketDataConfig.Mode mode, List<ComponentStatus> components,
            Instant checkedAt) {}
}
