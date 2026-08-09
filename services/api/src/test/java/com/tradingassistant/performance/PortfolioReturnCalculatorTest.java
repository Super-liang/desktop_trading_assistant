package com.tradingassistant.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PortfolioReturnCalculatorTest {
    @Test
    void stockUsesOpeningQuantityForDailyAndCurrentQuantityForHolding() {
        var result = PortfolioReturnCalculator.stock(bd("20"), bd("8"), bd("12"),
                bd("10"), bd("10"), true);

        assertThat(result.daily().profit()).isEqualByComparingTo("20");
        assertThat(result.daily().returnPercent()).isEqualByComparingTo("20");
        assertThat(result.holding().profit()).isEqualByComparingTo("80");
        assertThat(result.holding().returnPercent()).isEqualByComparingTo("50");
    }

    @Test
    void missingBaselineOrZeroCostOnlyDisablesAffectedMetric() {
        var result = PortfolioReturnCalculator.stock(bd("10"), BigDecimal.ZERO, bd("12"),
                null, null, false);

        assertThat(result.daily().available()).isFalse();
        assertThat(result.holding().available()).isFalse();
    }

    @Test
    void fundUsesAdjacentNavAndRequiresPositionFromEarlierDate() {
        var result = PortfolioReturnCalculator.fund(bd("100"), bd("1"), bd("1.2"),
                bd("1.1"), LocalDate.of(2026, 7, 27), LocalDate.of(2026, 7, 28));
        assertThat(result.daily().profit()).isEqualByComparingTo("10");
        assertThat(result.holding().profit()).isEqualByComparingTo("20");

        var sameDay = PortfolioReturnCalculator.fund(bd("100"), bd("1"), bd("1.2"),
                bd("1.1"), LocalDate.of(2026, 7, 28), LocalDate.of(2026, 7, 28));
        assertThat(sameDay.daily().available()).isFalse();
        assertThat(sameDay.holding().available()).isTrue();
    }

    private BigDecimal bd(String value) { return new BigDecimal(value); }
}
