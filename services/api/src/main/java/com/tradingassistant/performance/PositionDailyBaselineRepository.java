package com.tradingassistant.performance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionDailyBaselineRepository
        extends JpaRepository<PositionDailyBaseline, PositionDailyBaselineId> {
    List<PositionDailyBaseline> findAllByUserIdAndTradingDate(UUID userId, LocalDate tradingDate);
}
