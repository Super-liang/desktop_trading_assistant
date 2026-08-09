package com.tradingassistant.marketdata;

import com.tradingassistant.market.Market;
import com.tradingassistant.market.MarketClock;
import com.tradingassistant.market.MarketPhase;
import com.tradingassistant.market.MarketStatus;
import com.tradingassistant.portfolio.PortfolioItem;
import com.tradingassistant.portfolio.PortfolioRepository;
import com.tradingassistant.quote.Quote;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsPositionQuoteSchedulerTest {
    @Mock MarketClock marketClock;
    @Mock MarketDataConfigService configs;
    @Mock AkshareGatewayClient gateway;
    @Mock RedisMarketSnapshotRepository snapshots;
    @Mock PortfolioRepository positions;

    @Test
    void closedMarketDoesNotReadPositionsOrCallUpstream() {
        when(marketClock.status(Market.US_STOCK)).thenReturn(status(MarketPhase.CLOSED));
        var scheduler = scheduler();

        scheduler.refreshIfDue(Instant.parse("2026-08-07T21:00:00Z"));

        verifyNoInteractions(configs, gateway, snapshots, positions);
    }

    @Test
    void openMarketWithoutPositionsDoesNotCallUpstream() {
        openMarket();
        when(positions.findAllByMarketOrderByCreatedAtAsc(Market.US_STOCK))
                .thenReturn(List.of());

        scheduler().refreshIfDue(Instant.parse("2026-08-07T15:00:00Z"));

        verifyNoInteractions(gateway, snapshots);
    }

    @Test
    void openMarketQueriesDistinctHoldingSymbolsAndUpdatesSinaCache() {
        openMarket();
        PortfolioItem first = position("NASDAQ:AAPL");
        PortfolioItem duplicate = position("NASDAQ:AAPL");
        PortfolioItem second = position("NYSE:IBM");
        when(positions.findAllByMarketOrderByCreatedAtAsc(Market.US_STOCK))
                .thenReturn(List.of(first, duplicate, second));
        when(snapshots.acquireRefreshLock(Market.US_STOCK,
                MarketDataConfig.SnapshotSource.SINA, 30)).thenReturn(Optional.of("lock"));
        Quote quote = mock(Quote.class);
        when(gateway.usPositionQuotes(List.of("NASDAQ:AAPL", "NYSE:IBM")))
                .thenReturn(List.of(quote));

        scheduler().refreshIfDue(Instant.parse("2026-08-07T15:00:00Z"));

        verify(snapshots).replace(Market.US_STOCK,
                MarketDataConfig.SnapshotSource.SINA, List.of(quote));
        verify(snapshots).releaseRefreshLock(Market.US_STOCK,
                MarketDataConfig.SnapshotSource.SINA, "lock");
        verify(gateway, never()).marketSnapshot(eq(Market.US_STOCK), any());
    }

    @Test
    void upstreamFailurePreservesCacheAndReleasesLock() {
        openMarket();
        PortfolioItem position = position("NASDAQ:AAPL");
        when(positions.findAllByMarketOrderByCreatedAtAsc(Market.US_STOCK))
                .thenReturn(List.of(position));
        when(snapshots.acquireRefreshLock(Market.US_STOCK,
                MarketDataConfig.SnapshotSource.SINA, 30)).thenReturn(Optional.of("lock"));
        when(gateway.usPositionQuotes(anyList())).thenThrow(new IllegalStateException("down"));

        scheduler().refreshIfDue(Instant.parse("2026-08-07T15:00:00Z"));

        verify(snapshots, never()).replace(eq(Market.US_STOCK), any(), anyList());
        verify(snapshots).releaseRefreshLock(Market.US_STOCK,
                MarketDataConfig.SnapshotSource.SINA, "lock");
    }

    private UsPositionQuoteScheduler scheduler() {
        return new UsPositionQuoteScheduler(marketClock, configs, gateway, snapshots, positions);
    }

    private void openMarket() {
        when(marketClock.status(Market.US_STOCK)).thenReturn(status(MarketPhase.OPEN));
        when(configs.current()).thenReturn(MarketDataConfig.defaults());
    }

    private PortfolioItem position(String canonical) {
        PortfolioItem item = mock(PortfolioItem.class);
        when(item.canonical()).thenReturn(canonical);
        return item;
    }

    private MarketStatus status(MarketPhase phase) {
        return new MarketStatus(Market.US_STOCK, phase, null, null, "TEST", Instant.now(), true);
    }
}
