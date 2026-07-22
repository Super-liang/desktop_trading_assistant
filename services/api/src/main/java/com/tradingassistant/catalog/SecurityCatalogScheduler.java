package com.tradingassistant.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.quotes.http.enabled", havingValue = "true")
public class SecurityCatalogScheduler {
    private static final Logger log = LoggerFactory.getLogger(SecurityCatalogScheduler.class);
    private final SecurityCatalogService catalog;
    private final SecurityCatalogSyncService syncService;

    public SecurityCatalogScheduler(SecurityCatalogService catalog,
            SecurityCatalogSyncService syncService) {
        this.catalog = catalog;
        this.syncService = syncService;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Shanghai")
    public void dailySync() { syncSafely(); }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void syncEmptyCatalogAfterStartup() {
        if (catalog.isEmpty()) syncSafely();
    }

    private void syncSafely() {
        try {
            syncService.sync();
        } catch (RuntimeException exception) {
            log.warn("A 股证券目录本次同步未完成，将保留现有数据");
        }
    }
}
