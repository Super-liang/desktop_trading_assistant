package com.tradingassistant.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityCatalogSchedulerTest {
    @Mock SecurityCatalogService catalog;
    @Mock SecurityCatalogSyncService sync;

    @Test
    void schedulesDailySyncAtEightInShanghai() throws Exception {
        Scheduled scheduled = SecurityCatalogScheduler.class.getMethod("dailySync")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("0 0 8 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Shanghai");
    }

    @Test
    void asynchronouslyInitializesOnlyAnEmptyCatalog() {
        when(catalog.isEmpty()).thenReturn(true, false);
        SecurityCatalogScheduler scheduler = new SecurityCatalogScheduler(catalog, sync);

        scheduler.syncEmptyCatalogAfterStartup();
        scheduler.syncEmptyCatalogAfterStartup();

        verify(sync, times(1)).sync();
    }
}
