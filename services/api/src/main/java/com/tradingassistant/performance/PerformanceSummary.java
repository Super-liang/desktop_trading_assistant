package com.tradingassistant.performance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PerformanceSummary(
        BigDecimal dailyProfit,
        BigDecimal dailyReturnPercent,
        BigDecimal yearProfit,
        BigDecimal yearReturnPercent,
        BigDecimal annualizedReturnPercent,
        LocalDate statisticsStartDate,
        Instant calculatedAt,
        PerformanceStatus status,
        int missingQuoteCount,
        String referenceNotice) {
    public static final String REFERENCE_NOTICE =
            "参考收益：基于手工持仓和行情估算，不含成交时点、现金流、手续费、税费、分红及复权影响";
}
