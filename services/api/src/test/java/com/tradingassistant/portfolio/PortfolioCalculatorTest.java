package com.tradingassistant.portfolio;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PortfolioCalculatorTest {
    @Test
    void calculatesMarketValueProfitAndReturnWithDecimals() {
        var result = PortfolioCalculator.calculate(
                new BigDecimal("1000"),
                new BigDecimal("10.25"),
                new BigDecimal("11.10"));
        assertThat(result.marketValue()).isEqualByComparingTo("11100.00");
        assertThat(result.profit()).isEqualByComparingTo("850.00");
        assertThat(result.returnPercent()).isEqualByComparingTo("8.2927");
    }
}

