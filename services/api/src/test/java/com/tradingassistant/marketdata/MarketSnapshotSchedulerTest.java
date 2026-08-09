package com.tradingassistant.marketdata;

import com.tradingassistant.market.Market;
import com.tradingassistant.market.MarketClock;
import com.tradingassistant.market.MarketPhase;
import com.tradingassistant.market.MarketStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketSnapshotSchedulerTest {
    @Mock MarketDataConfigService configs;
    @Mock MarketClock marketClock;
    @Mock AkshareGatewayClient gateway;
    @Mock RedisMarketSnapshotRepository snapshots;

    @Test
    void doesNotCallUpstreamWhenCalendarIsUnknown() {
        when(configs.current()).thenReturn(MarketDataConfig.defaults());
        when(marketClock.status(Market.A_SHARE)).thenReturn(status(MarketPhase.UNKNOWN));
        var scheduler = new MarketSnapshotScheduler(marketClock, configs, gateway, snapshots);

        scheduler.refreshIfDue(Instant.parse("2026-07-22T04:00:00Z"));

        verifyNoInteractions(gateway, snapshots);
    }

    @Test
    void refreshesOnlySinaWhenItsDistributedLockIsAcquired() {
        MarketDataConfig config = MarketDataConfig.defaults();
        when(configs.current()).thenReturn(config);
        when(marketClock.status(Market.A_SHARE)).thenReturn(status(MarketPhase.OPEN));
        when(snapshots.acquireRefreshLock(MarketDataConfig.SnapshotSource.SINA, 30))
                .thenReturn(Optional.of("lock-SINA"));
        when(gateway.marketSnapshot(MarketDataConfig.SnapshotSource.SINA))
                .thenReturn(List.of(mock(com.tradingassistant.quote.Quote.class)));
        var scheduler = new MarketSnapshotScheduler(marketClock, configs, gateway, snapshots);

        scheduler.refreshIfDue(Instant.parse("2026-07-22T02:00:00Z"));

        verify(snapshots).replace(eq(MarketDataConfig.SnapshotSource.SINA), anyList());
        verify(snapshots).releaseRefreshLock(MarketDataConfig.SnapshotSource.SINA, "lock-SINA");
        verify(gateway, never()).marketSnapshot(MarketDataConfig.SnapshotSource.EASTMONEY);
        scheduler.shutdown();
    }

    @Test
    void refreshesSinaWhenServerDefaultModeIsSingleStock() {
        MarketDataConfig config = MarketDataConfig.defaults();
        config.update(MarketDataConfig.Provider.AKSHARE, MarketDataConfig.Mode.SINGLE_STOCK,
                MarketDataConfig.SnapshotSource.EASTMONEY,
                MarketDataConfig.SingleSource.XUEQIU, 30);
        when(configs.current()).thenReturn(config);
        when(marketClock.status(Market.A_SHARE)).thenReturn(status(MarketPhase.OPEN));
        when(snapshots.acquireRefreshLock(any(), eq(30)))
                .thenAnswer(invocation -> Optional.of("lock-" + invocation.getArgument(0)));
        when(gateway.marketSnapshot(any()))
                .thenReturn(List.of(mock(com.tradingassistant.quote.Quote.class)));
        var scheduler = new MarketSnapshotScheduler(marketClock, configs, gateway, snapshots);

        scheduler.refreshIfDue(Instant.parse("2026-07-22T02:00:00Z"));

        verify(gateway).marketSnapshot(MarketDataConfig.SnapshotSource.SINA);
        verify(gateway, never()).marketSnapshot(MarketDataConfig.SnapshotSource.EASTMONEY);
        scheduler.shutdown();
    }

    @Test
    void refreshesOpenMarketsInParallel() {
        when(configs.current()).thenReturn(MarketDataConfig.defaults());
        when(marketClock.status(Market.A_SHARE)).thenReturn(status(MarketPhase.OPEN));
        when(marketClock.status(Market.HK_STOCK)).thenReturn(status(Market.HK_STOCK, MarketPhase.OPEN));
        when(snapshots.acquireRefreshLock(MarketDataConfig.SnapshotSource.SINA, 30))
                .thenReturn(Optional.of("a-lock"));
        when(snapshots.acquireRefreshLock(Market.HK_STOCK,
                MarketDataConfig.SnapshotSource.SINA, 30)).thenReturn(Optional.of("hk-lock"));
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        when(gateway.marketSnapshot(MarketDataConfig.SnapshotSource.SINA)).thenAnswer(invocation -> {
            int current = active.incrementAndGet();
            maximum.accumulateAndGet(current, Math::max);
            Thread.sleep(80);
            active.decrementAndGet();
            return List.of(mock(com.tradingassistant.quote.Quote.class));
        });
        when(gateway.marketSnapshot(Market.HK_STOCK,
                MarketDataConfig.SnapshotSource.SINA)).thenAnswer(invocation -> {
            int current = active.incrementAndGet();
            maximum.accumulateAndGet(current, Math::max);
            Thread.sleep(80);
            active.decrementAndGet();
            return List.of(mock(com.tradingassistant.quote.Quote.class));
        });
        var scheduler = new MarketSnapshotScheduler(marketClock, configs, gateway, snapshots);

        scheduler.refreshIfDue(Instant.parse("2026-07-22T02:00:00Z"));

        assertThat(maximum).hasValue(2);
        scheduler.shutdown();
    }

    @Test
    void sinaFailureDoesNotOverwriteLastSnapshot() {
        when(configs.current()).thenReturn(MarketDataConfig.defaults());
        when(marketClock.status(Market.A_SHARE)).thenReturn(status(MarketPhase.OPEN));
        when(snapshots.acquireRefreshLock(any(), eq(30)))
                .thenAnswer(invocation -> Optional.of("lock-" + invocation.getArgument(0)));
        when(gateway.marketSnapshot(MarketDataConfig.SnapshotSource.SINA))
                .thenThrow(new IllegalStateException("upstream down"));
        var scheduler = new MarketSnapshotScheduler(marketClock, configs, gateway, snapshots);

        scheduler.refreshIfDue(Instant.parse("2026-07-22T02:00:00Z"));

        verify(snapshots, never()).replace(eq(MarketDataConfig.SnapshotSource.SINA), anyList());
        verify(snapshots).releaseRefreshLock(eq(MarketDataConfig.SnapshotSource.SINA), anyString());
        scheduler.shutdown();
    }

    @Test
    void refreshesOnlyFullMarketRoutesAndNeverRequestsUsFullSnapshot() {
        when(configs.current()).thenReturn(MarketDataConfig.defaults());
        when(marketClock.status(Market.A_SHARE)).thenReturn(status(MarketPhase.CLOSED));
        when(marketClock.status(Market.HK_STOCK)).thenReturn(status(
                Market.HK_STOCK, MarketPhase.OPEN));
        when(snapshots.acquireRefreshLock(any(Market.class), any(), eq(30)))
                .thenReturn(Optional.of("market-lock"));
        when(gateway.marketSnapshot(any(Market.class), any()))
                .thenReturn(List.of(mock(com.tradingassistant.quote.Quote.class)));
        var scheduler = new MarketSnapshotScheduler(marketClock, configs, gateway, snapshots);

        scheduler.refreshIfDue(Instant.parse("2026-07-22T14:00:00Z"));

        verify(gateway).marketSnapshot(Market.HK_STOCK,
                MarketDataConfig.SnapshotSource.SINA);
        verify(snapshots).replace(eq(Market.HK_STOCK),
                eq(MarketDataConfig.SnapshotSource.SINA), anyList());
        verify(gateway, never()).marketSnapshot(eq(Market.US_STOCK), any());
        verify(gateway, never()).marketSnapshot(any(Market.class),
                eq(MarketDataConfig.SnapshotSource.EASTMONEY));
        scheduler.shutdown();
    }

    private MarketStatus status(MarketPhase phase) {
        return status(Market.A_SHARE, phase);
    }

    private MarketStatus status(Market market, MarketPhase phase) {
        return new MarketStatus(market, phase, null, null, "TEST", Instant.now(),
                phase != MarketPhase.UNKNOWN);
    }
}
