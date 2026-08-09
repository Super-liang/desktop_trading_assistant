package com.tradingassistant.marketdata;

import com.tradingassistant.market.Market;
import com.tradingassistant.market.MarketClock;
import com.tradingassistant.market.MarketPhase;
import com.tradingassistant.performance.PositionBaselineService;
import java.time.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
@ConditionalOnProperty(
        name = {"app.market-data.scheduler-enabled", "app.quotes.http.enabled"},
        havingValue = "true")
public class MarketSnapshotScheduler {
    private static final Logger log = LoggerFactory.getLogger(MarketSnapshotScheduler.class);
    private final MarketClock marketClock;
    private final MarketDataConfigService configService;
    private final AkshareGatewayClient gateway;
    private final RedisMarketSnapshotRepository snapshots;
    private final PositionBaselineService baselines;
    private static final List<Route> ROUTES = List.of(
            new Route(Market.A_SHARE, MarketDataConfig.SnapshotSource.SINA),
            new Route(Market.HK_STOCK, MarketDataConfig.SnapshotSource.SINA));
    private final Map<Route, AtomicReference<Instant>> lastAttempts = new ConcurrentHashMap<>();
    private final ExecutorService refreshExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "market-snapshot-refresh");
        thread.setDaemon(true);
        return thread;
    });

    @Autowired
    public MarketSnapshotScheduler(MarketClock marketClock, MarketDataConfigService configService,
            AkshareGatewayClient gateway, RedisMarketSnapshotRepository snapshots,
            PositionBaselineService baselines) {
        this.marketClock = marketClock;
        this.configService = configService;
        this.gateway = gateway;
        this.snapshots = snapshots;
        this.baselines = baselines;
        ROUTES.forEach(route -> lastAttempts.put(route, new AtomicReference<>(Instant.EPOCH)));
    }

    MarketSnapshotScheduler(MarketClock marketClock, MarketDataConfigService configService,
            AkshareGatewayClient gateway, RedisMarketSnapshotRepository snapshots) {
        this(marketClock, configService, gateway, snapshots, null);
    }

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        refreshIfDue(Instant.now());
    }

    void refreshIfDue(Instant now) {
        MarketDataConfig config = configService.current();
        CompletableFuture<?>[] refreshes = ROUTES.stream()
                .filter(route -> {
                    var status = marketClock.status(route.market());
                    return status != null && status.phase() == MarketPhase.OPEN;
                })
                .map(route -> CompletableFuture.runAsync(
                        () -> refreshSourceIfDue(route, config.getRefreshSeconds(), now), refreshExecutor))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(refreshes).join();
    }

    private void refreshSourceIfDue(Route route,
            int refreshSeconds, Instant now) {
        AtomicReference<Instant> lastAttempt = lastAttempts.get(route);
        Instant previous = lastAttempt.get();
        if (Duration.between(previous, now).toSeconds() < refreshSeconds) return;
        if (!lastAttempt.compareAndSet(previous, now)) return;
        var lockToken = route.market() == Market.A_SHARE
                ? snapshots.acquireRefreshLock(route.source(), refreshSeconds)
                : snapshots.acquireRefreshLock(route.market(), route.source(), refreshSeconds);
        if (lockToken.isEmpty()) return;
        try {
            var quotes = route.market() == Market.A_SHARE
                    ? gateway.marketSnapshot(route.source())
                    : gateway.marketSnapshot(route.market(), route.source());
            if (route.market() == Market.A_SHARE) snapshots.replace(route.source(), quotes);
            else snapshots.replace(route.market(), route.source(), quotes);
            if (baselines != null && route.source() == MarketDataConfig.SnapshotSource.SINA) {
                baselines.capture(route.market(), quotes, now);
            }
            log.info("AKShare 全市场快照刷新成功：market={},source={},count={}",
                    route.market(), route.source(), quotes.size());
        } catch (RuntimeException exception) {
            // 保留最后成功快照；只记录来源和异常类型，不输出持仓或响应正文。
            log.warn("AKShare 全市场快照刷新失败：market={},source={},error={}",
                    route.market(), route.source(), exception.getClass().getSimpleName());
        } finally {
            if (route.market() == Market.A_SHARE) {
                snapshots.releaseRefreshLock(route.source(), lockToken.get());
            } else {
                snapshots.releaseRefreshLock(route.market(), route.source(), lockToken.get());
            }
        }
    }

    @PreDestroy
    void shutdown() {
        refreshExecutor.shutdownNow();
    }

    private record Route(Market market, MarketDataConfig.SnapshotSource source) {}

}
