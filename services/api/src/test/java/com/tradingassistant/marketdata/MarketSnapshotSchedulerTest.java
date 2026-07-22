package com.tradingassistant.marketdata;

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
    @Mock AkshareGatewayClient gateway;
    @Mock RedisMarketSnapshotRepository snapshots;

    @Test
    void identifiesShanghaiTradingSessions() {
        assertThat(MarketSnapshotScheduler.isTradingTime(Instant.parse("2026-07-22T01:15:00Z"))).isTrue();
        assertThat(MarketSnapshotScheduler.isTradingTime(Instant.parse("2026-07-22T03:30:00Z"))).isTrue();
        assertThat(MarketSnapshotScheduler.isTradingTime(Instant.parse("2026-07-22T03:30:01Z"))).isFalse();
        assertThat(MarketSnapshotScheduler.isTradingTime(Instant.parse("2026-07-22T04:00:00Z"))).isFalse();
        assertThat(MarketSnapshotScheduler.isTradingTime(Instant.parse("2026-07-22T05:00:00Z"))).isTrue();
        assertThat(MarketSnapshotScheduler.isTradingTime(Instant.parse("2026-07-22T07:00:00Z"))).isTrue();
        assertThat(MarketSnapshotScheduler.isTradingTime(Instant.parse("2026-07-22T07:00:01Z"))).isFalse();
        assertThat(MarketSnapshotScheduler.isTradingTime(Instant.parse("2026-07-25T02:00:00Z"))).isFalse();
    }

    @Test
    void doesNotCallUpstreamOutsideTradingHours() {
        when(configs.current()).thenReturn(MarketDataConfig.defaults());
        var scheduler = new MarketSnapshotScheduler(configs, gateway, snapshots);

        scheduler.refreshIfDue(Instant.parse("2026-07-22T04:00:00Z"));

        verifyNoInteractions(gateway, snapshots);
    }

    @Test
    void refreshesBothSourcesWhenTheirDistributedLocksAreAcquired() {
        MarketDataConfig config = MarketDataConfig.defaults();
        when(configs.current()).thenReturn(config);
        for (MarketDataConfig.SnapshotSource source : MarketDataConfig.SnapshotSource.values()) {
            when(snapshots.acquireRefreshLock(source, 30))
                    .thenReturn(Optional.of("lock-" + source));
            when(gateway.marketSnapshot(source))
                    .thenReturn(List.of(mock(com.tradingassistant.quote.Quote.class)));
        }
        var scheduler = new MarketSnapshotScheduler(configs, gateway, snapshots);

        scheduler.refreshIfDue(Instant.parse("2026-07-22T02:00:00Z"));

        for (MarketDataConfig.SnapshotSource source : MarketDataConfig.SnapshotSource.values()) {
            verify(snapshots).replace(eq(source), anyList());
            verify(snapshots).releaseRefreshLock(source, "lock-" + source);
        }
        scheduler.shutdown();
    }

    @Test
    void refreshesBothSourcesWhenServerDefaultModeIsSingleStock() {
        MarketDataConfig config = MarketDataConfig.defaults();
        config.update(MarketDataConfig.Provider.AKSHARE, MarketDataConfig.Mode.SINGLE_STOCK,
                MarketDataConfig.SnapshotSource.EASTMONEY,
                MarketDataConfig.SingleSource.XUEQIU, 30);
        when(configs.current()).thenReturn(config);
        when(snapshots.acquireRefreshLock(any(), eq(30)))
                .thenAnswer(invocation -> Optional.of("lock-" + invocation.getArgument(0)));
        when(gateway.marketSnapshot(any()))
                .thenReturn(List.of(mock(com.tradingassistant.quote.Quote.class)));
        var scheduler = new MarketSnapshotScheduler(configs, gateway, snapshots);

        scheduler.refreshIfDue(Instant.parse("2026-07-22T02:00:00Z"));

        verify(gateway).marketSnapshot(MarketDataConfig.SnapshotSource.SINA);
        verify(gateway).marketSnapshot(MarketDataConfig.SnapshotSource.EASTMONEY);
        scheduler.shutdown();
    }

    @Test
    void refreshesSourcesInParallel() {
        when(configs.current()).thenReturn(MarketDataConfig.defaults());
        when(snapshots.acquireRefreshLock(any(), eq(30)))
                .thenAnswer(invocation -> Optional.of("lock-" + invocation.getArgument(0)));
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        when(gateway.marketSnapshot(any())).thenAnswer(invocation -> {
            int current = active.incrementAndGet();
            maximum.accumulateAndGet(current, Math::max);
            Thread.sleep(80);
            active.decrementAndGet();
            return List.of(mock(com.tradingassistant.quote.Quote.class));
        });
        var scheduler = new MarketSnapshotScheduler(configs, gateway, snapshots);

        scheduler.refreshIfDue(Instant.parse("2026-07-22T02:00:00Z"));

        assertThat(maximum).hasValue(2);
        scheduler.shutdown();
    }

    @Test
    void oneSourceFailureDoesNotPreventTheOtherFromBeingStored() {
        when(configs.current()).thenReturn(MarketDataConfig.defaults());
        when(snapshots.acquireRefreshLock(any(), eq(30)))
                .thenAnswer(invocation -> Optional.of("lock-" + invocation.getArgument(0)));
        when(gateway.marketSnapshot(MarketDataConfig.SnapshotSource.SINA))
                .thenThrow(new IllegalStateException("upstream down"));
        when(gateway.marketSnapshot(MarketDataConfig.SnapshotSource.EASTMONEY))
                .thenReturn(List.of(mock(com.tradingassistant.quote.Quote.class)));
        var scheduler = new MarketSnapshotScheduler(configs, gateway, snapshots);

        scheduler.refreshIfDue(Instant.parse("2026-07-22T02:00:00Z"));

        verify(snapshots).replace(eq(MarketDataConfig.SnapshotSource.EASTMONEY), anyList());
        verify(snapshots, never()).replace(eq(MarketDataConfig.SnapshotSource.SINA), anyList());
        verify(snapshots).releaseRefreshLock(eq(MarketDataConfig.SnapshotSource.SINA), anyString());
        verify(snapshots).releaseRefreshLock(eq(MarketDataConfig.SnapshotSource.EASTMONEY), anyString());
        scheduler.shutdown();
    }
}
