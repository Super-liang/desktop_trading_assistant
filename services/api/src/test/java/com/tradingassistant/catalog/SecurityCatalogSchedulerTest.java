package com.tradingassistant.catalog;

import com.tradingassistant.market.Market;
import com.tradingassistant.market.MarketClock;
import com.tradingassistant.market.MarketPhase;
import com.tradingassistant.market.MarketStatus;
import java.time.Instant;
import java.time.LocalDate;
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
    @Mock CatalogSyncCoordinator coordinator;
    @Mock MarketClock marketClock;
    @Mock FundNavSyncService fundNavSync;

    @Test
    void stockCatalogsRunEveryTenMinutesOnlyDuringPreOpenWindow() throws Exception {
        Scheduled scheduled = SecurityCatalogScheduler.class
                .getMethod("syncStockCatalogsBeforeOpen").getAnnotation(Scheduled.class);
        assertThat(scheduled.cron()).isEqualTo("0 */10 * * * *");

        Instant aOpen = Instant.parse("2026-07-30T01:30:00Z");
        when(marketClock.status(Market.A_SHARE)).thenReturn(new MarketStatus(
                Market.A_SHARE, MarketPhase.PRE_OPEN, aOpen, null, "XSHG", Instant.EPOCH, true));
        when(marketClock.status(Market.HK_STOCK)).thenReturn(new MarketStatus(
                Market.HK_STOCK, MarketPhase.CLOSED, null, null, "XHKG", Instant.EPOCH, true));
        when(marketClock.status(Market.US_STOCK)).thenReturn(new MarketStatus(
                Market.US_STOCK, MarketPhase.UNKNOWN, null, null, null, null, false));
        SecurityCatalogScheduler scheduler = new SecurityCatalogScheduler(
                catalog, coordinator, marketClock, Runnable::run);

        scheduler.syncStockCatalogsBeforeOpen();

        verify(coordinator).synchronize(Market.A_SHARE, LocalDate.of(2026, 7, 30));
        verifyNoMoreInteractions(coordinator);
    }

    @Test
    void fundCatalogRunsAtSevenInShanghai() throws Exception {
        Scheduled scheduled = SecurityCatalogScheduler.class
                .getMethod("syncFundCatalogAtSeven").getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("0 0 7 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Shanghai");

        SecurityCatalogScheduler scheduler = new SecurityCatalogScheduler(
                catalog, coordinator, marketClock, fundNavSync, Runnable::run);
        scheduler.syncFundCatalogAtSeven();
        verify(fundNavSync).synchronize();
    }

    @Test
    void startupInitializesEveryEmptyMarket() {
        when(catalog.isEmpty(any())).thenAnswer(invocation ->
                invocation.getArgument(0) == Market.HK_STOCK);
        SecurityCatalogScheduler scheduler = new SecurityCatalogScheduler(
                catalog, coordinator, marketClock, Runnable::run);

        scheduler.syncEmptyCatalogsAfterStartup();

        verify(coordinator).synchronize(eq(Market.HK_STOCK), any(LocalDate.class));
        verify(coordinator, times(1)).synchronize(any(), any());
    }
}
