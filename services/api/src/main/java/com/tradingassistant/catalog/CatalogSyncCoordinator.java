package com.tradingassistant.catalog;

import com.tradingassistant.market.Market;
import java.time.Instant;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.quotes.http.enabled", havingValue = "true")
public class CatalogSyncCoordinator {
    static final String JOB_TYPE = "SECURITY_CATALOG";
    private static final Logger log = LoggerFactory.getLogger(CatalogSyncCoordinator.class);
    private final MarketSyncRunRepository runs;
    private final SecurityCatalogSyncService syncService;

    public CatalogSyncCoordinator(MarketSyncRunRepository runs,
            SecurityCatalogSyncService syncService) {
        this.runs = runs;
        this.syncService = syncService;
    }

    public void synchronize(Market market, LocalDate tradingDate) {
        MarketSyncRun run = runs.findByMarketAndTradingDateAndJobType(
                        market, tradingDate, JOB_TYPE)
                .orElseGet(() -> new MarketSyncRun(market, tradingDate, JOB_TYPE, Instant.now()));
        if (run.getStatus() == MarketSyncRun.Status.SUCCESS) return;
        run.retry(Instant.now());
        try {
            runs.saveAndFlush(run);
        } catch (DataIntegrityViolationException concurrentRun) {
            log.info("证券目录任务已由其他实例领取：market={},tradingDate={}", market, tradingDate);
            return;
        }
        try {
            if (!syncService.sync(market)) {
                throw new IllegalStateException("证券目录同步锁正由其他实例持有");
            }
            run.succeed(Instant.now());
            runs.save(run);
        } catch (RuntimeException exception) {
            run.fail(Instant.now(), exception);
            runs.save(run);
            throw exception;
        }
    }
}
