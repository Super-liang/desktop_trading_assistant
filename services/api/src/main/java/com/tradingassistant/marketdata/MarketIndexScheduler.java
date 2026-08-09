package com.tradingassistant.marketdata;

import com.tradingassistant.market.Market;
import com.tradingassistant.market.MarketClock;
import com.tradingassistant.market.MarketPhase;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = {"app.market-data.scheduler-enabled", "app.quotes.http.enabled"},
        havingValue = "true")
public class MarketIndexScheduler {
    private static final Logger log = LoggerFactory.getLogger(MarketIndexScheduler.class);
    private final MarketClock marketClock;
    private final MarketDataConfigService config;
    private final AkshareGatewayClient gateway;
    private final RedisIndexOverviewRepository repository;
    private final Map<MarketDataConfig.SnapshotSource, AtomicReference<Instant>> lastAttempts =
            new EnumMap<>(MarketDataConfig.SnapshotSource.class);
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "market-index-refresh");
        thread.setDaemon(true);
        return thread;
    });

    public MarketIndexScheduler(MarketClock marketClock, MarketDataConfigService config,
            AkshareGatewayClient gateway, RedisIndexOverviewRepository repository) {
        this.marketClock = marketClock;
        this.config = config;
        this.gateway = gateway;
        this.repository = repository;
        lastAttempts.put(MarketDataConfig.SnapshotSource.SINA,
                new AtomicReference<>(Instant.EPOCH));
    }

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        if (marketClock.status(Market.A_SHARE).phase() != MarketPhase.OPEN) return;
        Instant now = Instant.now();
        int refreshSeconds = config.current().getRefreshSeconds();
        CompletableFuture.runAsync(() -> refreshIfDue(
                MarketDataConfig.SnapshotSource.SINA, refreshSeconds, now), executor).join();
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void initializeMissingSnapshots() {
        Instant now = Instant.now();
        var source = MarketDataConfig.SnapshotSource.SINA;
        if (repository.find(source).isEmpty()) {
            // 首次部署若处于非交易时段，也需要建立一份可展示的最后行情。
            refreshIfDue(source, 0, now);
        }
    }

    void refreshIfDue(MarketDataConfig.SnapshotSource source, int seconds, Instant now) {
        AtomicReference<Instant> state = lastAttempts.get(source);
        Instant old = state.get();
        if (Duration.between(old, now).toSeconds() < seconds || !state.compareAndSet(old, now)) return;
        try {
            List<MarketIndexQuote> quotes = gateway.indexOverview(source);
            repository.merge(source, quotes, now);
            log.info("A 股指数快照刷新成功：source={},count={}", source, quotes.size());
        } catch (RuntimeException exception) {
            log.warn("A 股指数快照刷新失败并保留旧值：source={},error={}",
                    source, exception.getClass().getSimpleName());
        }
    }

    @PreDestroy
    void shutdown() { executor.shutdownNow(); }
}
