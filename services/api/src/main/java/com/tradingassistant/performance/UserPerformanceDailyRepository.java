package com.tradingassistant.performance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPerformanceDailyRepository
        extends JpaRepository<UserPerformanceDaily, UserPerformanceDaily.Id> {
    Optional<UserPerformanceDaily> findTopByIdUserIdOrderByIdTradingDateDesc(UUID userId);
    List<UserPerformanceDaily> findAllByIdUserIdAndIdTradingDateBetweenOrderByIdTradingDateAsc(
            UUID userId, LocalDate from, LocalDate to);
}
