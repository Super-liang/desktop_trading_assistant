package com.tradingassistant.performance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceCalculatorTest {
    @Test
    void calculatesIntradayProfitAndRate() {
        var result = PerformanceCalculator.intraday(List.of(
                new PerformanceCalculator.PositionQuote(new BigDecimal("100"),
                        new BigDecimal("11"), new BigDecimal("10"), true),
                new PerformanceCalculator.PositionQuote(new BigDecimal("200"),
                        new BigDecimal("4.5"), new BigDecimal("5"), true)));

        assertThat(result.dailyProfit()).isEqualByComparingTo("0.00");
        assertThat(result.dailyReturnPercent()).isEqualByComparingTo("0.0000");
        assertThat(result.status()).isEqualTo(PerformanceStatus.COMPLETE);
        assertThat(result.missingQuoteCount()).isZero();
    }

    @Test
    void doesNotInventRateForZeroBaseline() {
        var result = PerformanceCalculator.intraday(List.of(
                new PerformanceCalculator.PositionQuote(BigDecimal.ZERO,
                        BigDecimal.TEN, BigDecimal.TEN, true)));

        assertThat(result.dailyProfit()).isNull();
        assertThat(result.dailyReturnPercent()).isNull();
        assertThat(result.status()).isEqualTo(PerformanceStatus.UNAVAILABLE);
    }

    @Test
    void marksPartialQuotesAndCountsMissingPositions() {
        var result = PerformanceCalculator.intraday(List.of(
                new PerformanceCalculator.PositionQuote(BigDecimal.TEN,
                        new BigDecimal("12"), BigDecimal.TEN, true),
                new PerformanceCalculator.PositionQuote(BigDecimal.TEN,
                        null, null, false)));

        assertThat(result.dailyProfit()).isEqualByComparingTo("20.00");
        assertThat(result.dailyReturnPercent()).isEqualByComparingTo("20.0000");
        assertThat(result.status()).isEqualTo(PerformanceStatus.PARTIAL);
        assertThat(result.missingQuoteCount()).isEqualTo(1);
    }

    @Test
    void compoundsOnlyCurrentCalendarYearAndAnnualizesAfterThirtyDays() {
        List<PerformanceCalculator.DailyReturn> history = List.of(
                new PerformanceCalculator.DailyReturn(LocalDate.of(2025, 12, 31),
                        new BigDecimal("50"), new BigDecimal("10")),
                new PerformanceCalculator.DailyReturn(LocalDate.of(2026, 1, 2),
                        new BigDecimal("10"), new BigDecimal("10")),
                new PerformanceCalculator.DailyReturn(LocalDate.of(2026, 2, 2),
                        new BigDecimal("20"), new BigDecimal("-10")));

        var result = PerformanceCalculator.yearToDate(LocalDate.of(2026, 2, 2), history);

        assertThat(result.yearProfit()).isEqualByComparingTo("30.00");
        assertThat(result.yearReturnPercent()).isEqualByComparingTo("-1.0000");
        assertThat(result.annualizedReturnPercent()).isNotNull();
        assertThat(result.statisticsStartDate()).isEqualTo(LocalDate.of(2026, 1, 2));
    }

    @Test
    void annualizedRateAccumulatesForFirstThirtyNaturalDays() {
        var result = PerformanceCalculator.yearToDate(LocalDate.of(2026, 1, 20), List.of(
                new PerformanceCalculator.DailyReturn(LocalDate.of(2026, 1, 2),
                        BigDecimal.ONE, BigDecimal.ONE)));

        assertThat(result.annualizedReturnPercent()).isNull();
        assertThat(result.status()).isEqualTo(PerformanceStatus.ACCUMULATING);
    }
}
