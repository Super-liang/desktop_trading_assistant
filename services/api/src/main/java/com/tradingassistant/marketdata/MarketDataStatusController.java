package com.tradingassistant.marketdata;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public StatusView status(
            @RequestParam(required = false) MarketDataConfig.Mode mode,
            @RequestParam(required = false) MarketDataConfig.SingleSource singleSource) {
        MarketDataConfig config = configs.current();
        MarketDataConfig.Mode selectedMode = mode == null ? config.getMode() : mode;
        List<ComponentStatus> components = new ArrayList<>();
        components.add(new ComponentStatus("SPRING_API", "Spring API", "UP",
                Instant.now(), 0L, "服务正常"));
        try {
            AkshareGatewayClient.GatewayHealth health = gateway.health();
            components.add(new ComponentStatus("AKSHARE_GATEWAY", "AKShare 网关",
                    "UP".equals(health.status()) ? "UP" : "DEGRADED", null, null,
                    health.status()));
        } catch (RuntimeException exception) {
            components.add(new ComponentStatus("AKSHARE_GATEWAY", "AKShare 网关", "DOWN",
                    null, null, exception.getClass().getSimpleName()));
        }

        if (selectedMode == MarketDataConfig.Mode.SINGLE_STOCK) {
            components.add(new ComponentStatus("REDIS_SNAPSHOT", "Redis 快照",
                    "NOT_APPLICABLE", null, null, "单股模式不使用全市场缓存"));
        } else {
            boolean redisUp;
            try {
                redisUp = snapshots.ping();
            } catch (RuntimeException exception) {
                redisUp = false;
            }
            for (MarketDataConfig.SnapshotSource source : MarketDataConfig.SnapshotSource.values()) {
                components.add(snapshotStatus(config, source, redisUp));
            }
        }

        MarketDataConfig.SingleSource selectedSingleSource = singleSource == null
                ? config.getSingleSource() : singleSource;
        List<String> sourceIds = selectedMode == MarketDataConfig.Mode.MARKET_SNAPSHOT
                ? java.util.Arrays.stream(MarketDataConfig.SnapshotSource.values())
                        .map(source -> "SNAPSHOT_" + source.name()).toList()
                : List.of("SINGLE_" + selectedSingleSource.name());
        try {
            List<AkshareGatewayClient.SourceHealth> statuses = gateway.sourceStatus();
            for (String sourceId : sourceIds) {
                AkshareGatewayClient.SourceHealth selected = statuses.stream()
                        .filter(item -> sourceId.equals(item.source())).findFirst().orElse(null);
                components.add(selected == null
                        ? new ComponentStatus("UPSTREAM_" + sourceId, sourceId, "UNKNOWN",
                                null, null, "尚未调用")
                        : new ComponentStatus("UPSTREAM_" + sourceId, sourceId, selected.status(),
                                selected.lastSuccessAt(), null,
                                selected.errorType() == null
                                        ? selected.latencyMillis() + " ms" : selected.errorType()));
            }
        } catch (RuntimeException exception) {
            for (String sourceId : sourceIds) {
                components.add(new ComponentStatus("UPSTREAM_" + sourceId, sourceId, "UNKNOWN",
                        null, null, "网关状态不可用"));
            }
        }
        return new StatusView(selectedMode, components, Instant.now());
    }

    private ComponentStatus snapshotStatus(MarketDataConfig config,
            MarketDataConfig.SnapshotSource source, boolean redisUp) {
        String id = "REDIS_SNAPSHOT_" + source.name();
        String label = "Redis " + (source == MarketDataConfig.SnapshotSource.SINA ? "新浪" : "东财");
        try {
            if (!redisUp) throw new IllegalStateException("Redis 未响应 PONG");
            var metadata = snapshots.metadata(source);
            if (metadata.isEmpty()) {
                return new ComponentStatus(id, label, "UNKNOWN", null, null, "等待首次快照");
            }
            Instant now = Instant.now();
            long age = Math.max(0, Duration.between(metadata.get().fetchedAt(), now).toSeconds());
            boolean overdueDuringTrading = MarketSnapshotScheduler.isTradingTime(now)
                    && age > config.getRefreshSeconds() * 2L;
            return new ComponentStatus(id, label, overdueDuringTrading ? "DEGRADED" : "UP",
                    metadata.get().fetchedAt(), age, metadata.get().quoteCount() + " 只证券");
        } catch (RuntimeException exception) {
            return new ComponentStatus(id, label, "DOWN", null, null,
                    exception.getClass().getSimpleName());
        }
    }

    public record ComponentStatus(String id, String label, String status, Instant lastSuccessAt,
            Long ageSeconds, String detail) {}
    public record StatusView(MarketDataConfig.Mode mode, List<ComponentStatus> components,
            Instant checkedAt) {}
}
