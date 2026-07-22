package com.tradingassistant.marketdata;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
        assertThat(MarketSnapshotScheduler.isTradingTime(Instant.parse("2026-07-22T04:00:00Z"))).isFalse();
        assertThat(MarketSnapshotScheduler.isTradingTime(Instant.parse("2026-07-22T05:00:00Z"))).isTrue();
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
    void storesSnapshotOnlyWhenDistributedLockIsAcquired() {
        MarketDataConfig config = MarketDataConfig.defaults();
        when(configs.current()).thenReturn(config);
        when(snapshots.acquireRefreshLock(10)).thenReturn(Optional.of("lock-token"));
        when(gateway.marketSnapshot(MarketDataConfig.SnapshotSource.EASTMONEY))
                .thenReturn(List.of(mock(com.tradingassistant.quote.Quote.class)));
        var scheduler = new MarketSnapshotScheduler(configs, gateway, snapshots);

        scheduler.refreshIfDue(Instant.parse("2026-07-22T02:00:00Z"));

        verify(snapshots).replace(eq(MarketDataConfig.SnapshotSource.EASTMONEY), anyList());
        verify(snapshots).releaseRefreshLock("lock-token");
    }
}
