package com.tradingassistant.marketdata;

import java.time.*;
import java.util.EnumMap;
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

@Component
@ConditionalOnProperty(
        name = {"app.market-data.scheduler-enabled", "app.quotes.http.enabled"},
        havingValue = "true")
public class MarketSnapshotScheduler {
    private static final Logger log = LoggerFactory.getLogger(MarketSnapshotScheduler.class);
    private static final ZoneId CHINA = ZoneId.of("Asia/Shanghai");
    private final MarketDataConfigService configService;
    private final AkshareGatewayClient gateway;
    private final RedisMarketSnapshotRepository snapshots;
    private final Map<MarketDataConfig.SnapshotSource, AtomicReference<Instant>> lastAttempts =
            new EnumMap<>(MarketDataConfig.SnapshotSource.class);
    private final ExecutorService refreshExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "market-snapshot-refresh");
        thread.setDaemon(true);
        return thread;
    });

    public MarketSnapshotScheduler(MarketDataConfigService configService,
            AkshareGatewayClient gateway, RedisMarketSnapshotRepository snapshots) {
        this.configService = configService;
        this.gateway = gateway;
        this.snapshots = snapshots;
        for (MarketDataConfig.SnapshotSource source : MarketDataConfig.SnapshotSource.values()) {
            lastAttempts.put(source, new AtomicReference<>(Instant.EPOCH));
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        refreshIfDue(Instant.now());
    }

    void refreshIfDue(Instant now) {
        MarketDataConfig config = configService.current();
        if (!isTradingTime(now)) return;
        CompletableFuture<?>[] refreshes = java.util.Arrays.stream(MarketDataConfig.SnapshotSource.values())
                .map(source -> CompletableFuture.runAsync(
                        () -> refreshSourceIfDue(source, config.getRefreshSeconds(), now), refreshExecutor))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(refreshes).join();
    }

    private void refreshSourceIfDue(MarketDataConfig.SnapshotSource source,
            int refreshSeconds, Instant now) {
        AtomicReference<Instant> lastAttempt = lastAttempts.get(source);
        Instant previous = lastAttempt.get();
        if (Duration.between(previous, now).toSeconds() < refreshSeconds) return;
        if (!lastAttempt.compareAndSet(previous, now)) return;
        var lockToken = snapshots.acquireRefreshLock(source, refreshSeconds);
        if (lockToken.isEmpty()) return;
        try {
            var quotes = gateway.marketSnapshot(source);
            snapshots.replace(source, quotes);
            log.info("AKShare 全市场快照刷新成功：source={},count={}",
                    source, quotes.size());
        } catch (RuntimeException exception) {
            // 保留最后成功快照；只记录来源和异常类型，不输出持仓或响应正文。
            log.warn("AKShare 全市场快照刷新失败：source={},error={}",
                    source, exception.getClass().getSimpleName());
        } finally {
            snapshots.releaseRefreshLock(source, lockToken.get());
        }
    }

    @PreDestroy
    void shutdown() {
        refreshExecutor.shutdownNow();
    }

    static boolean isTradingTime(Instant instant) {
        ZonedDateTime local = instant.atZone(CHINA);
        if (local.getDayOfWeek() == DayOfWeek.SATURDAY
                || local.getDayOfWeek() == DayOfWeek.SUNDAY) return false;
        LocalTime time = local.toLocalTime();
        return (!time.isBefore(LocalTime.of(9, 15)) && !time.isAfter(LocalTime.of(11, 30)))
                || (!time.isBefore(LocalTime.of(13, 0)) && !time.isAfter(LocalTime.of(15, 0)));
    }
}
