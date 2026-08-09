package com.tradingassistant.marketdata;

import com.tradingassistant.market.Market;
import com.tradingassistant.market.MarketClock;
import com.tradingassistant.market.MarketPhase;
import com.tradingassistant.performance.PositionBaselineService;
import com.tradingassistant.portfolio.PortfolioRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = {"app.market-data.scheduler-enabled", "app.quotes.http.enabled"},
        havingValue = "true")
public class UsPositionQuoteScheduler {
    private static final Logger log = LoggerFactory.getLogger(UsPositionQuoteScheduler.class);
    private static final int MAX_INSTRUMENTS_PER_REFRESH = 2000;
    private final MarketClock marketClock;
    private final MarketDataConfigService configs;
    private final AkshareGatewayClient gateway;
    private final RedisMarketSnapshotRepository snapshots;
    private final PortfolioRepository positions;
    private final PositionBaselineService baselines;
    private final AtomicReference<Instant> lastAttempt = new AtomicReference<>(Instant.EPOCH);

    @Autowired
    public UsPositionQuoteScheduler(MarketClock marketClock, MarketDataConfigService configs,
            AkshareGatewayClient gateway, RedisMarketSnapshotRepository snapshots,
            PortfolioRepository positions, PositionBaselineService baselines) {
        this.marketClock = marketClock;
        this.configs = configs;
        this.gateway = gateway;
        this.snapshots = snapshots;
        this.positions = positions;
        this.baselines = baselines;
    }

    UsPositionQuoteScheduler(MarketClock marketClock, MarketDataConfigService configs,
            AkshareGatewayClient gateway, RedisMarketSnapshotRepository snapshots,
            PortfolioRepository positions) {
        this(marketClock, configs, gateway, snapshots, positions, null);
    }

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        refreshIfDue(Instant.now());
    }

    void refreshIfDue(Instant now) {
        var status = marketClock.status(Market.US_STOCK);
        if (status == null || status.phase() != MarketPhase.OPEN) return;
        int refreshSeconds = configs.current().getRefreshSeconds();
        Instant previous = lastAttempt.get();
        if (Duration.between(previous, now).toSeconds() < refreshSeconds
                || !lastAttempt.compareAndSet(previous, now)) return;

        List<String> instruments = positions.findAllByMarketOrderByCreatedAtAsc(Market.US_STOCK)
                .stream().map(item -> item.canonical().toUpperCase()).distinct().sorted()
                .limit(MAX_INSTRUMENTS_PER_REFRESH).toList();
        if (instruments.isEmpty()) return;

        var source = MarketDataConfig.SnapshotSource.SINA;
        var lockToken = snapshots.acquireRefreshLock(Market.US_STOCK, source, refreshSeconds);
        if (lockToken.isEmpty()) return;
        try {
            var quotes = gateway.usPositionQuotes(instruments);
            if (quotes.isEmpty()) {
                log.warn("新浪美股持仓行情未返回可用数据：requested_count={}", instruments.size());
                return;
            }
            // Redis 更新脚本会保留未返回证券，只覆盖本次成功字段，缓存不设置过期时间。
            snapshots.replace(Market.US_STOCK, source, quotes);
            if (baselines != null) baselines.capture(Market.US_STOCK, quotes, now);
            log.info("新浪美股持仓行情刷新成功：requested_count={},updated_count={}",
                    instruments.size(), quotes.size());
        } catch (RuntimeException exception) {
            log.warn("新浪美股持仓行情刷新失败：requested_count={},error={}",
                    instruments.size(), exception.getClass().getSimpleName());
        } finally {
            snapshots.releaseRefreshLock(Market.US_STOCK, source, lockToken.get());
        }
    }
}
