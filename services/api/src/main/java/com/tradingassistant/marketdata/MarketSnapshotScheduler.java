package com.tradingassistant.marketdata;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
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
    private final AtomicReference<Instant> lastAttempt = new AtomicReference<>(Instant.EPOCH);

    public MarketSnapshotScheduler(MarketDataConfigService configService,
            AkshareGatewayClient gateway, RedisMarketSnapshotRepository snapshots) {
        this.configService = configService;
        this.gateway = gateway;
        this.snapshots = snapshots;
    }

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        refreshIfDue(Instant.now());
    }

    void refreshIfDue(Instant now) {
        MarketDataConfig config = configService.current();
        if (config.getMode() != MarketDataConfig.Mode.MARKET_SNAPSHOT || !isTradingTime(now)) return;
        Instant previous = lastAttempt.get();
        if (Duration.between(previous, now).toSeconds() < config.getRefreshSeconds()) return;
        if (!lastAttempt.compareAndSet(previous, now)) return;
        var lockToken = snapshots.acquireRefreshLock(config.getRefreshSeconds());
        if (lockToken.isEmpty()) return;
        try {
            var quotes = gateway.marketSnapshot(config.getSnapshotSource());
            snapshots.replace(config.getSnapshotSource(), quotes);
            log.info("AKShare 全市场快照刷新成功：source={},count={}",
                    config.getSnapshotSource(), quotes.size());
        } catch (RuntimeException exception) {
            // 保留最后成功快照；只记录来源和异常类型，不输出持仓或响应正文。
            log.warn("AKShare 全市场快照刷新失败：source={},error={}",
                    config.getSnapshotSource(), exception.getClass().getSimpleName());
        } finally {
            snapshots.releaseRefreshLock(lockToken.get());
        }
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
