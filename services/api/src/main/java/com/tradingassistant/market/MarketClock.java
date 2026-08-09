package com.tradingassistant.market;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MarketClock {
    private final MarketSessionRepository sessions;
    private final Clock clock;

    @Autowired
    public MarketClock(MarketSessionRepository sessions) {
        this(sessions, Clock.systemUTC());
    }

    MarketClock(MarketSessionRepository sessions, Clock clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    public MarketStatus status(Market market) {
        if (market == Market.PUBLIC_FUND) {
            return new MarketStatus(market, MarketPhase.CLOSED, null, null,
                    "NAV_DATE", null, true);
        }
        Instant now = clock.instant();
        LocalDate localDate = now.atZone(market.timezone()).toLocalDate();
        Optional<MarketSession> first = sessions.findFirstByMarketOrderByTradingDateAsc(market);
        Optional<MarketSession> last = sessions.findFirstByMarketOrderByTradingDateDesc(market);
        if (first.isEmpty() || last.isEmpty()
                || localDate.isBefore(first.get().getTradingDate())
                || localDate.isAfter(last.get().getTradingDate())) {
            return new MarketStatus(market, MarketPhase.UNKNOWN, null, null,
                    null, null, false);
        }

        Optional<MarketSession> current = sessions.findByMarketAndTradingDate(market, localDate);
        if (current.isEmpty()) {
            Instant nextOpen = nextSession(market, localDate).map(MarketSession::getOpenAt).orElse(null);
            return new MarketStatus(market, MarketPhase.HOLIDAY, nextOpen, null,
                    last.get().getSource(), last.get().getSyncedAt(), true);
        }

        MarketSession session = current.get();
        MarketPhase phase;
        if (now.isBefore(session.getOpenAt().minusSeconds(3600))) phase = MarketPhase.CLOSED;
        else if (now.isBefore(session.getOpenAt())) phase = MarketPhase.PRE_OPEN;
        else if (session.getBreakStartAt() != null && !now.isBefore(session.getBreakStartAt())
                && now.isBefore(session.getBreakEndAt())) phase = MarketPhase.BREAK;
        else if (now.isBefore(session.getCloseAt())) phase = MarketPhase.OPEN;
        else phase = MarketPhase.CLOSED;

        Instant nextOpen = now.isBefore(session.getOpenAt()) ? session.getOpenAt()
                : nextSession(market, localDate).map(MarketSession::getOpenAt).orElse(null);
        Instant nextClose = now.isBefore(session.getCloseAt()) ? session.getCloseAt() : null;
        return new MarketStatus(market, phase, nextOpen, nextClose,
                session.getSource(), session.getSyncedAt(), true);
    }

    private Optional<MarketSession> nextSession(Market market, LocalDate date) {
        return sessions.findFirstByMarketAndTradingDateGreaterThanOrderByTradingDateAsc(market, date);
    }
}
