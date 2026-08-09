package com.tradingassistant.market;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketClockTest {
    private static final Instant SYNCED = Instant.parse("2026-07-28T18:30:00Z");

    @Test
    void identifiesAShareLunchBreak() {
        MarketSessionRepository repository = repositoryWith(todaySession(Market.A_SHARE));
        MarketClock clock = new MarketClock(repository,
                Clock.fixed(Instant.parse("2026-07-29T04:00:00Z"), ZoneOffset.UTC));

        assertThat(clock.status(Market.A_SHARE).phase()).isEqualTo(MarketPhase.BREAK);
    }

    @Test
    void identifiesOpenBeforeEarlyCloseAndClosedAfterIt() {
        MarketSession early = new MarketSession(Market.US_STOCK, LocalDate.of(2026, 7, 29),
                "America/New_York", Instant.parse("2026-07-29T13:30:00Z"), null, null,
                Instant.parse("2026-07-29T17:00:00Z"), true, "TEST", false, SYNCED);
        MarketSessionRepository repository = repositoryWith(early);

        assertThat(new MarketClock(repository, Clock.fixed(Instant.parse("2026-07-29T16:00:00Z"), ZoneOffset.UTC))
                .status(Market.US_STOCK).phase()).isEqualTo(MarketPhase.OPEN);
        assertThat(new MarketClock(repository, Clock.fixed(Instant.parse("2026-07-29T17:01:00Z"), ZoneOffset.UTC))
                .status(Market.US_STOCK).phase()).isEqualTo(MarketPhase.CLOSED);
    }

    @Test
    void returnsUnknownOutsideCalendarCoverage() {
        MarketSession session = todaySession(Market.HK_STOCK);
        MarketSessionRepository repository = mock(MarketSessionRepository.class);
        when(repository.findFirstByMarketOrderByTradingDateAsc(Market.HK_STOCK)).thenReturn(Optional.of(session));
        when(repository.findFirstByMarketOrderByTradingDateDesc(Market.HK_STOCK)).thenReturn(Optional.of(session));
        MarketClock clock = new MarketClock(repository,
                Clock.fixed(Instant.parse("2027-07-29T04:00:00Z"), ZoneOffset.UTC));

        assertThat(clock.status(Market.HK_STOCK).phase()).isEqualTo(MarketPhase.UNKNOWN);
        assertThat(clock.status(Market.HK_STOCK).calendarAvailable()).isFalse();
    }

    @Test
    void manualOverrideIsNotReplacedByProviderRefresh() {
        MarketSession manual = new MarketSession(Market.A_SHARE, LocalDate.of(2026, 7, 29),
                "Asia/Shanghai", Instant.parse("2026-07-29T02:00:00Z"), null, null,
                Instant.parse("2026-07-29T06:00:00Z"), true, "MANUAL", true, SYNCED);
        MarketSession provider = todaySession(Market.A_SHARE);

        manual.refreshFrom(provider);

        assertThat(manual.getSource()).isEqualTo("MANUAL");
        assertThat(manual.getOpenAt()).isEqualTo(Instant.parse("2026-07-29T02:00:00Z"));
    }

    private MarketSessionRepository repositoryWith(MarketSession session) {
        MarketSessionRepository repository = mock(MarketSessionRepository.class);
        Market market = session.getMarket();
        when(repository.findFirstByMarketOrderByTradingDateAsc(market)).thenReturn(Optional.of(session));
        when(repository.findFirstByMarketOrderByTradingDateDesc(market)).thenReturn(Optional.of(session));
        when(repository.findByMarketAndTradingDate(market, session.getTradingDate())).thenReturn(Optional.of(session));
        when(repository.findFirstByMarketAndTradingDateGreaterThanOrderByTradingDateAsc(market, session.getTradingDate()))
                .thenReturn(Optional.empty());
        return repository;
    }

    private MarketSession todaySession(Market market) {
        return new MarketSession(market, LocalDate.of(2026, 7, 29), market.timezone().getId(),
                Instant.parse("2026-07-29T01:30:00Z"), Instant.parse("2026-07-29T03:30:00Z"),
                Instant.parse("2026-07-29T05:00:00Z"), Instant.parse("2026-07-29T07:00:00Z"),
                false, "TEST", false, SYNCED);
    }
}
