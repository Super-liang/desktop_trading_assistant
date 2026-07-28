package com.tradingassistant.performance;

import com.tradingassistant.auth.User;
import com.tradingassistant.auth.UserRepository;
import com.tradingassistant.marketdata.MarketDataConfig;
import com.tradingassistant.portfolio.PortfolioItem;
import com.tradingassistant.portfolio.PortfolioRepository;
import com.tradingassistant.quote.InstrumentId;
import com.tradingassistant.quote.QuoteProviderRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PerformanceSettlementSchedulerTest {
    @Mock UserRepository users;
    @Mock PortfolioRepository portfolios;
    @Mock UserPerformanceDailyRepository daily;
    @Mock QuoteProviderRegistry quotes;

    @Test
    void skipsKnownExchangeHoliday() {
        var scheduler = scheduler();
        scheduler.settle(LocalDate.of(2026, 5, 1));
        verifyNoInteractions(users, portfolios, daily, quotes);
    }

    @Test
    void repeatsSettlementByUpdatingSameUserDateRecord() {
        User user = new User("settle@example.com", "结算用户", "hash", User.Role.USER);
        PortfolioItem item = new PortfolioItem(user.getId(), InstrumentId.parse("SSE:600519"),
                "贵州茅台", BigDecimal.TEN, BigDecimal.ONE, 0);
        when(users.findAllByStatus(User.Status.ACTIVE)).thenReturn(List.of(user));
        when(portfolios.findAllByUserIdOrderBySortOrderAscCreatedAtAsc(user.getId()))
                .thenReturn(List.of(item));
        when(daily.findById(any())).thenReturn(Optional.empty(), Optional.of(mock(UserPerformanceDaily.class)));
        when(quotes.snapshots(anyList(), argThat(options ->
                options.mode() == MarketDataConfig.Mode.MARKET_SNAPSHOT
                        && options.snapshotSource() == MarketDataConfig.SnapshotSource.EASTMONEY)))
                .thenReturn(List.of());
        var scheduler = scheduler();

        scheduler.settle(LocalDate.of(2026, 7, 22));
        scheduler.settle(LocalDate.of(2026, 7, 22));

        verify(daily, times(2)).findById(new UserPerformanceDaily.Id(
                user.getId(), LocalDate.of(2026, 7, 22)));
        verify(daily, times(2)).save(any());
    }

    private PerformanceSettlementScheduler scheduler() {
        return new PerformanceSettlementScheduler(users, portfolios, daily, quotes,
                new ChinaTradingCalendar());
    }
}
