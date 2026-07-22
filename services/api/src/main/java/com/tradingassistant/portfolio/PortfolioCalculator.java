package com.tradingassistant.portfolio;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PortfolioCalculator {
    private PortfolioCalculator() {}

    public static Result calculate(BigDecimal quantity, BigDecimal costPrice, BigDecimal last) {
        if (quantity.signum() == 0 || costPrice.signum() == 0) {
            return new Result(BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                    BigDecimal.ZERO.setScale(4));
        }
        BigDecimal marketValue = last.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
        BigDecimal profit = last.subtract(costPrice).multiply(quantity)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal returnPercent = last.subtract(costPrice).multiply(BigDecimal.valueOf(100))
                .divide(costPrice, 4, RoundingMode.HALF_UP);
        return new Result(marketValue, profit, returnPercent);
    }

    public record Result(BigDecimal marketValue, BigDecimal profit, BigDecimal returnPercent) {}
}
