package com.tradingassistant.performance;

import com.tradingassistant.market.Currency;
import com.tradingassistant.market.Market;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PortfolioReturns(List<Group> groups, Instant calculatedAt, String calculationNotice) {
    public static final String NOTICE =
            "参考收益：按市场和币种分别计算，不含手续费、税费、现金流、分红及复权影响";

    public record Group(Market market, Currency currency, BigDecimal dailyProfit,
            BigDecimal dailyReturnPercent, BigDecimal holdingProfit,
            BigDecimal holdingReturnPercent, PerformanceStatus dailyStatus,
            int unavailableDailyCount, List<Item> items) {}

    public record Item(UUID positionId, String instrumentId, String displayName,
            Market market, Currency currency, BigDecimal currentPrice, LocalDate valueDate,
            Instant quoteAsOf, boolean delayed, boolean stale,
            BigDecimal dailyProfit, BigDecimal dailyReturnPercent,
            BigDecimal holdingProfit, BigDecimal holdingReturnPercent,
            PerformanceStatus dailyStatus, String unavailableReason,
            BigDecimal dailyBaseValue, BigDecimal holdingBaseValue) {}
}
