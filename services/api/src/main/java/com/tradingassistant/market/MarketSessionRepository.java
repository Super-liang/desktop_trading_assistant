package com.tradingassistant.market;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketSessionRepository extends JpaRepository<MarketSession, MarketSessionId> {
    Optional<MarketSession> findByMarketAndTradingDate(Market market, LocalDate tradingDate);
    Optional<MarketSession> findFirstByMarketOrderByTradingDateAsc(Market market);
    Optional<MarketSession> findFirstByMarketOrderByTradingDateDesc(Market market);
    Optional<MarketSession> findFirstByMarketAndTradingDateGreaterThanOrderByTradingDateAsc(
            Market market, LocalDate tradingDate);
}
