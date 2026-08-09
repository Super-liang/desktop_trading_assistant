package com.tradingassistant.catalog;

import com.tradingassistant.market.Market;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogSyncCoordinatorTest {
    @Mock MarketSyncRunRepository runs;
    @Mock SecurityCatalogSyncService sync;

    @Test
    void skipsAlreadySuccessfulRun() {
        LocalDate date = LocalDate.of(2026, 7, 30);
        MarketSyncRun run = new MarketSyncRun(Market.HK_STOCK, date,
                CatalogSyncCoordinator.JOB_TYPE, java.time.Instant.EPOCH);
        run.succeed(java.time.Instant.EPOCH);
        when(runs.findByMarketAndTradingDateAndJobType(
                Market.HK_STOCK, date, CatalogSyncCoordinator.JOB_TYPE))
                .thenReturn(Optional.of(run));

        new CatalogSyncCoordinator(runs, sync).synchronize(Market.HK_STOCK, date);

        verifyNoInteractions(sync);
        verify(runs, never()).save(any());
    }

    @Test
    void failedRunIsRecordedAndCanBeRetriedLater() {
        LocalDate date = LocalDate.of(2026, 7, 30);
        when(runs.findByMarketAndTradingDateAndJobType(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(runs.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("upstream unavailable"))
                .when(sync).sync(Market.US_STOCK);
        CatalogSyncCoordinator coordinator = new CatalogSyncCoordinator(runs, sync);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> coordinator.synchronize(Market.US_STOCK, date))
                .isInstanceOf(IllegalStateException.class);

        verify(runs).save(argThat(run -> run.getStatus() == MarketSyncRun.Status.FAILED));
    }
}
