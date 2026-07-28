package com.tradingassistant.performance;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class PerformanceCalculator {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private PerformanceCalculator() {}

    public static IntradayResult intraday(List<PositionQuote> positions) {
        if (positions.isEmpty()) {
            return new IntradayResult(BigDecimal.ZERO.setScale(2),
                    BigDecimal.ZERO.setScale(4), PerformanceStatus.COMPLETE, 0);
        }
        BigDecimal profit = BigDecimal.ZERO;
        BigDecimal baseline = BigDecimal.ZERO;
        int missing = 0;
        for (PositionQuote position : positions) {
            if (!position.available() || position.last() == null
                    || position.previousClose() == null
                    || position.previousClose().signum() <= 0) {
                missing++;
                continue;
            }
            profit = profit.add(position.last().subtract(position.previousClose())
                    .multiply(position.quantity()));
            baseline = baseline.add(position.previousClose().multiply(position.quantity()));
        }
        if (baseline.signum() == 0) {
            return new IntradayResult(null, null, PerformanceStatus.UNAVAILABLE, missing);
        }
        BigDecimal rate = profit.multiply(ONE_HUNDRED)
                .divide(baseline, 4, RoundingMode.HALF_UP);
        return new IntradayResult(profit.setScale(2, RoundingMode.HALF_UP), rate,
                missing == 0 ? PerformanceStatus.COMPLETE : PerformanceStatus.PARTIAL, missing);
    }

    public static YearResult yearToDate(LocalDate asOf, List<DailyReturn> history) {
        List<DailyReturn> currentYear = history.stream()
                .filter(item -> item.date().getYear() == asOf.getYear())
                .filter(item -> !item.date().isAfter(asOf))
                .sorted(java.util.Comparator.comparing(DailyReturn::date)).toList();
        if (currentYear.isEmpty()) {
            return new YearResult(null, null, null, null, PerformanceStatus.ACCUMULATING);
        }
        BigDecimal profit = currentYear.stream().map(DailyReturn::profit)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal factor = BigDecimal.ONE;
        for (DailyReturn item : currentYear) {
            factor = factor.multiply(BigDecimal.ONE.add(
                    item.returnPercent().divide(ONE_HUNDRED, MathContext.DECIMAL64)),
                    MathContext.DECIMAL64);
        }
        BigDecimal yearRate = factor.subtract(BigDecimal.ONE).multiply(ONE_HUNDRED)
                .setScale(4, RoundingMode.HALF_UP);
        LocalDate start = currentYear.get(0).date();
        long days = ChronoUnit.DAYS.between(start, asOf) + 1;
        if (days < 30 || factor.signum() <= 0) {
            return new YearResult(profit, yearRate, null, start,
                    PerformanceStatus.ACCUMULATING);
        }
        double annualFactor = Math.pow(factor.doubleValue(), 365d / days);
        BigDecimal annualized = BigDecimal.valueOf((annualFactor - 1d) * 100d)
                .setScale(4, RoundingMode.HALF_UP);
        return new YearResult(profit, yearRate, annualized, start,
                PerformanceStatus.COMPLETE);
    }

    public record PositionQuote(BigDecimal quantity, BigDecimal last,
                                BigDecimal previousClose, boolean available) {}
    public record IntradayResult(BigDecimal dailyProfit, BigDecimal dailyReturnPercent,
                                 PerformanceStatus status, int missingQuoteCount) {}
    public record DailyReturn(LocalDate date, BigDecimal profit, BigDecimal returnPercent) {}
    public record YearResult(BigDecimal yearProfit, BigDecimal yearReturnPercent,
                             BigDecimal annualizedReturnPercent, LocalDate statisticsStartDate,
                             PerformanceStatus status) {}
}
