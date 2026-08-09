package com.tradingassistant.catalog;

import com.tradingassistant.market.Market;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketSyncRunRepository extends JpaRepository<MarketSyncRun, UUID> {
    Optional<MarketSyncRun> findByMarketAndTradingDateAndJobType(
            Market market, LocalDate tradingDate, String jobType);
}
