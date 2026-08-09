package com.tradingassistant.catalog;

import com.tradingassistant.market.Market;
import com.tradingassistant.market.MarketClock;
import com.tradingassistant.market.MarketPhase;
import com.tradingassistant.market.MarketStatus;
import jakarta.annotation.PreDestroy;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.quotes.http.enabled", havingValue = "true")
public class SecurityCatalogScheduler {
    private static final Logger log = LoggerFactory.getLogger(SecurityCatalogScheduler.class);
    private static final List<Market> STOCK_MARKETS = List.of(
            Market.A_SHARE, Market.HK_STOCK, Market.US_STOCK);
    private final SecurityCatalogService catalog;
    private final CatalogSyncCoordinator coordinator;
    private final MarketClock marketClock;
    private final FundNavSyncService fundNavSync;
    private final Executor executor;
    private final ExecutorService ownedExecutor;

    @Autowired
    public SecurityCatalogScheduler(SecurityCatalogService catalog,
            CatalogSyncCoordinator coordinator, MarketClock marketClock,
            FundNavSyncService fundNavSync) {
        this(catalog, coordinator, marketClock, fundNavSync, newExecutor());
    }

    SecurityCatalogScheduler(SecurityCatalogService catalog,
            CatalogSyncCoordinator coordinator, MarketClock marketClock, Executor executor) {
        this(catalog, coordinator, marketClock, null, executor);
    }

    SecurityCatalogScheduler(SecurityCatalogService catalog,
            CatalogSyncCoordinator coordinator, MarketClock marketClock,
            FundNavSyncService fundNavSync, Executor executor) {
        this.catalog = catalog;
        this.coordinator = coordinator;
        this.marketClock = marketClock;
        this.fundNavSync = fundNavSync;
        this.executor = executor;
        this.ownedExecutor = executor instanceof ExecutorService service ? service : null;
    }

    @Scheduled(cron = "0 */10 * * * *")
    public void syncStockCatalogsBeforeOpen() {
        for (Market market : STOCK_MARKETS) {
            MarketStatus status = marketClock.status(market);
            if (status.phase() != MarketPhase.PRE_OPEN || status.nextOpenAt() == null) continue;
            LocalDate tradingDate = status.nextOpenAt().atZone(market.timezone()).toLocalDate();
            executor.execute(() -> syncSafely(market, tradingDate));
        }
    }

    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Shanghai")
    public void syncFundCatalogAtSeven() {
        syncSafely(Market.PUBLIC_FUND, LocalDate.now(ZoneId.of("Asia/Shanghai")));
        if (fundNavSync != null) {
            try {
                fundNavSync.synchronize();
            } catch (RuntimeException exception) {
                log.warn("基金单位净值本次同步未完成，将保留历史净值：error={}",
                        exception.getClass().getSimpleName());
            }
        }
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void syncEmptyCatalogsAfterStartup() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        for (Market market : Market.values()) {
            if (catalog.isEmpty(market)) executor.execute(() -> syncSafely(market, today));
        }
    }

    private void syncSafely(Market market, LocalDate tradingDate) {
        try {
            coordinator.synchronize(market, tradingDate);
        } catch (RuntimeException exception) {
            log.warn("证券目录本次同步未完成，将保留现有数据：market={},error={}",
                    market, exception.getClass().getSimpleName());
        }
    }

    private static ExecutorService newExecutor() {
        return Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "catalog-sync");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    void shutdown() {
        if (ownedExecutor != null) ownedExecutor.shutdownNow();
    }
}
