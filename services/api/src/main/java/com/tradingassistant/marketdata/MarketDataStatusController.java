package com.tradingassistant.marketdata;

import com.tradingassistant.market.Market;
import com.tradingassistant.market.MarketClock;
import com.tradingassistant.market.MarketPhase;
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
    private static final List<Market> STOCK_MARKETS = List.of(
            Market.A_SHARE, Market.HK_STOCK, Market.US_STOCK);
    private final MarketDataConfigService configs;
    private final RedisMarketSnapshotRepository snapshots;
    private final AkshareGatewayClient gateway;
    private final MarketClock marketClock;

    public MarketDataStatusController(MarketDataConfigService configs,
            RedisMarketSnapshotRepository snapshots, AkshareGatewayClient gateway,
            MarketClock marketClock) {
        this.configs = configs;
        this.snapshots = snapshots;
        this.gateway = gateway;
        this.marketClock = marketClock;
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
            for (Market market : STOCK_MARKETS) {
                components.add(snapshotStatus(config, market, redisUp));
            }
        }

        MarketDataConfig.SingleSource selectedSingleSource = singleSource == null
                ? config.getSingleSource() : singleSource;
        List<String> sourceIds = selectedMode == MarketDataConfig.Mode.MARKET_SNAPSHOT
                ? STOCK_MARKETS.stream()
                        .map(market -> market == Market.US_STOCK
                                ? "US_STOCK:POSITION:SINA"
                                : market.name() + ":SNAPSHOT:SINA").toList()
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
                                        ? selected.latencyMillis() == null ? "尚未调用"
                                                : selected.latencyMillis() + " ms"
                                        : selected.errorType()));
            }
        } catch (RuntimeException exception) {
            for (String sourceId : sourceIds) {
                components.add(new ComponentStatus("UPSTREAM_" + sourceId, sourceId, "UNKNOWN",
                        null, null, "网关状态不可用"));
            }
        }
        return new StatusView(selectedMode, components, Instant.now());
    }

    private ComponentStatus snapshotStatus(MarketDataConfig config, Market market,
            boolean redisUp) {
        String id = "REDIS_SNAPSHOT_" + market.name() + "_SINA";
        String label = marketLabel(market) + "新浪缓存";
        try {
            if (!redisUp) throw new IllegalStateException("Redis 未响应 PONG");
            var metadata = snapshots.metadata(market, MarketDataConfig.SnapshotSource.SINA);
            if (metadata.isEmpty()) {
                return new ComponentStatus(id, label, "UNKNOWN", null, null, "等待首次快照");
            }
            Instant now = Instant.now();
            long age = Math.max(0, Duration.between(metadata.get().fetchedAt(), now).toSeconds());
            boolean overdueDuringTrading = marketClock.status(market).phase() == MarketPhase.OPEN
                    && age > config.getRefreshSeconds() * 2L;
            return new ComponentStatus(id, label, overdueDuringTrading ? "DEGRADED" : "UP",
                    metadata.get().fetchedAt(), age, metadata.get().quoteCount() + " 只证券");
        } catch (RuntimeException exception) {
            return new ComponentStatus(id, label, "DOWN", null, null,
                    exception.getClass().getSimpleName());
        }
    }

    private String marketLabel(Market market) {
        return switch (market) {
            case A_SHARE -> "A股";
            case HK_STOCK -> "港股";
            case US_STOCK -> "美股";
            case PUBLIC_FUND -> "公募基金";
        };
    }

    public record ComponentStatus(String id, String label, String status, Instant lastSuccessAt,
            Long ageSeconds, String detail) {}
    public record StatusView(MarketDataConfig.Mode mode, List<ComponentStatus> components,
            Instant checkedAt) {}
}
