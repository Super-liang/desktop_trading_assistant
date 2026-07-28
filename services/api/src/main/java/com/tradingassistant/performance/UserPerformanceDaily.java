package com.tradingassistant.performance;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_performance_daily")
public class UserPerformanceDaily {
    @EmbeddedId private Id id;
    @Column(name = "daily_profit", precision = 24, scale = 4) private BigDecimal dailyProfit;
    @Column(name = "daily_return_percent", precision = 16, scale = 6)
    private BigDecimal dailyReturnPercent;
    @Column(name = "year_profit", precision = 24, scale = 4) private BigDecimal yearProfit;
    @Column(name = "year_return_percent", precision = 16, scale = 6)
    private BigDecimal yearReturnPercent;
    @Column(name = "annualized_return_percent", precision = 16, scale = 6)
    private BigDecimal annualizedReturnPercent;
    @Column(name = "statistics_start_date") private LocalDate statisticsStartDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private PerformanceStatus status;
    @Column(name = "missing_quote_count", nullable = false) private int missingQuoteCount;
    @Column(name = "quote_source", nullable = false, length = 40) private String quoteSource;
    @Column(name = "calculated_at", nullable = false) private Instant calculatedAt;

    protected UserPerformanceDaily() {}

    public UserPerformanceDaily(UUID userId, LocalDate tradingDate) {
        this.id = new Id(userId, tradingDate);
    }

    public void update(PerformanceCalculator.IntradayResult day,
            PerformanceCalculator.YearResult year, String source, Instant at) {
        this.dailyProfit = day.dailyProfit();
        this.dailyReturnPercent = day.dailyReturnPercent();
        this.yearProfit = year.yearProfit();
        this.yearReturnPercent = year.yearReturnPercent();
        this.annualizedReturnPercent = year.annualizedReturnPercent();
        this.statisticsStartDate = year.statisticsStartDate();
        this.status = day.status() == PerformanceStatus.UNAVAILABLE
                || day.status() == PerformanceStatus.PARTIAL ? day.status() : year.status();
        this.missingQuoteCount = day.missingQuoteCount();
        this.quoteSource = source;
        this.calculatedAt = at;
    }

    public Id getId() { return id; }
    public BigDecimal getDailyProfit() { return dailyProfit; }
    public BigDecimal getDailyReturnPercent() { return dailyReturnPercent; }
    public BigDecimal getYearProfit() { return yearProfit; }
    public BigDecimal getYearReturnPercent() { return yearReturnPercent; }
    public BigDecimal getAnnualizedReturnPercent() { return annualizedReturnPercent; }
    public LocalDate getStatisticsStartDate() { return statisticsStartDate; }
    public PerformanceStatus getStatus() { return status; }
    public int getMissingQuoteCount() { return missingQuoteCount; }
    public Instant getCalculatedAt() { return calculatedAt; }

    @Embeddable
    public static class Id implements Serializable {
        @Column(name = "user_id") private UUID userId;
        @Column(name = "trading_date") private LocalDate tradingDate;
        protected Id() {}
        public Id(UUID userId, LocalDate tradingDate) {
            this.userId = userId;
            this.tradingDate = tradingDate;
        }
        public UUID getUserId() { return userId; }
        public LocalDate getTradingDate() { return tradingDate; }
        @Override public boolean equals(Object other) {
            return other instanceof Id value && Objects.equals(userId, value.userId)
                    && Objects.equals(tradingDate, value.tradingDate);
        }
        @Override public int hashCode() { return Objects.hash(userId, tradingDate); }
    }
}
