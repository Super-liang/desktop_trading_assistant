package com.tradingassistant.performance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** 纯收益计算规则，不负责行情、持仓或汇率数据的读取。 */
public final class PortfolioReturnCalculator {
    private static final int SCALE = 6;

    private PortfolioReturnCalculator() {}

    public static Result stock(BigDecimal currentQuantity, BigDecimal costPrice,
            BigDecimal currentPrice, BigDecimal openingQuantity, BigDecimal openingPrice,
            boolean dailyAvailable) {
        Metric holding = holding(currentQuantity, costPrice, currentPrice);
        Metric daily = dailyAvailable
                ? change(openingQuantity, openingPrice, currentPrice) : Metric.unavailable();
        return new Result(daily, holding);
    }

    public static Result fund(BigDecimal currentQuantity, BigDecimal costPrice,
            BigDecimal currentNav, BigDecimal previousNav, LocalDate openedOn,
            LocalDate currentNavDate) {
        Metric holding = holding(currentQuantity, costPrice, currentNav);
        boolean eligible = currentNavDate != null && openedOn != null
                && openedOn.isBefore(currentNavDate);
        Metric daily = eligible ? change(currentQuantity, previousNav, currentNav)
                : Metric.unavailable();
        return new Result(daily, holding);
    }

    private static Metric holding(BigDecimal quantity, BigDecimal cost, BigDecimal current) {
        if (!positive(quantity) || !positive(cost) || !positive(current)) return Metric.unavailable();
        return change(quantity, cost, current);
    }

    private static Metric change(BigDecimal quantity, BigDecimal base, BigDecimal current) {
        if (!positive(quantity) || !positive(base) || !positive(current)) return Metric.unavailable();
        BigDecimal profit = current.subtract(base).multiply(quantity).setScale(4, RoundingMode.HALF_UP);
        BigDecimal rate = current.subtract(base).divide(base, SCALE + 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal baseValue = base.multiply(quantity).setScale(4, RoundingMode.HALF_UP);
        return new Metric(profit, rate, baseValue, true);
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    public record Result(Metric daily, Metric holding) {}
    public record Metric(BigDecimal profit, BigDecimal returnPercent,
                         BigDecimal baseValue, boolean available) {
        static Metric unavailable() { return new Metric(null, null, null, false); }
    }
}
