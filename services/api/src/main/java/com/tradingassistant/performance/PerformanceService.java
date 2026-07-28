package com.tradingassistant.performance;

import com.tradingassistant.marketdata.MarketDataConfig;
import com.tradingassistant.portfolio.PortfolioItem;
import com.tradingassistant.portfolio.PortfolioRepository;
import com.tradingassistant.quote.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PerformanceService {
    private static final ZoneId CHINA = ZoneId.of("Asia/Shanghai");
    private final PortfolioRepository portfolios;
    private final QuoteProviderRegistry quotes;
    private final UserPerformanceDailyRepository daily;

    public PerformanceService(PortfolioRepository portfolios, QuoteProviderRegistry quotes,
            UserPerformanceDailyRepository daily) {
        this.portfolios = portfolios;
        this.quotes = quotes;
        this.daily = daily;
    }

    public PerformanceSummary current(UUID userId) {
        LocalDate today = LocalDate.now(CHINA);
        List<PortfolioItem> owned = portfolios.findAllByUserIdOrderBySortOrderAscCreatedAtAsc(userId);
        Map<String, Quote> latest = latest(owned);
        var intraday = PerformanceCalculator.intraday(owned.stream().map(item -> {
            Quote quote = latest.get(item.canonical());
            return new PerformanceCalculator.PositionQuote(item.getQuantity(),
                    quote == null ? null : quote.last(),
                    quote == null ? null : quote.previousClose(), quote != null);
        }).toList());
        Optional<UserPerformanceDaily> settled = daily
                .findAllByIdUserIdAndIdTradingDateBetweenOrderByIdTradingDateAsc(
                        userId, today.withDayOfYear(1), today).stream().reduce((first, second) -> second);
        PerformanceStatus status = intraday.status() == PerformanceStatus.COMPLETE
                ? settled.map(UserPerformanceDaily::getStatus)
                        .orElse(PerformanceStatus.ACCUMULATING)
                : intraday.status();
        return new PerformanceSummary(intraday.dailyProfit(), intraday.dailyReturnPercent(),
                settled.map(UserPerformanceDaily::getYearProfit).orElse(null),
                settled.map(UserPerformanceDaily::getYearReturnPercent).orElse(null),
                settled.map(UserPerformanceDaily::getAnnualizedReturnPercent).orElse(null),
                settled.map(UserPerformanceDaily::getStatisticsStartDate).orElse(null),
                Instant.now(), status, intraday.missingQuoteCount(),
                PerformanceSummary.REFERENCE_NOTICE);
    }

    private Map<String, Quote> latest(List<PortfolioItem> owned) {
        if (owned.isEmpty()) return Map.of();
        try {
            return quotes.snapshots(owned.stream().map(item ->
                            InstrumentId.parse(item.canonical())).toList())
                    .stream().collect(Collectors.toMap(Quote::instrumentId, value -> value,
                            (first, ignored) -> first));
        } catch (QuoteUnavailableException exception) {
            return Map.of();
        }
    }
}
