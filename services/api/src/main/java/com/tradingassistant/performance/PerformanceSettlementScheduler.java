package com.tradingassistant.performance;

import com.tradingassistant.auth.User;
import com.tradingassistant.auth.UserRepository;
import com.tradingassistant.marketdata.MarketDataConfig;
import com.tradingassistant.portfolio.PortfolioItem;
import com.tradingassistant.portfolio.PortfolioRepository;
import com.tradingassistant.quote.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PerformanceSettlementScheduler {
    private static final Logger log = LoggerFactory.getLogger(PerformanceSettlementScheduler.class);
    private static final ZoneId CHINA = ZoneId.of("Asia/Shanghai");
    private static final String SETTLEMENT_SOURCE = "AKSHARE_EASTMONEY_SNAPSHOT";
    private final UserRepository users;
    private final PortfolioRepository portfolios;
    private final UserPerformanceDailyRepository daily;
    private final QuoteProviderRegistry quotes;
    private final ChinaTradingCalendar calendar;

    public PerformanceSettlementScheduler(UserRepository users, PortfolioRepository portfolios,
            UserPerformanceDailyRepository daily, QuoteProviderRegistry quotes,
            ChinaTradingCalendar calendar) {
        this.users = users;
        this.portfolios = portfolios;
        this.daily = daily;
        this.quotes = quotes;
        this.calendar = calendar;
    }

    // 15:10 首次结算，15:20/15:30 对行情延迟或短暂故障做幂等重试。
    @Scheduled(cron = "0 10,20,30 15 * * MON-FRI", zone = "Asia/Shanghai")
    @Transactional
    public void scheduledSettlement() {
        settle(LocalDate.now(CHINA));
    }

    @Transactional
    public void settle(LocalDate date) {
        if (!calendar.isTradingDay(date)) return;
        for (User user : users.findAllByStatus(User.Status.ACTIVE)) {
            settleUser(user.getId(), date);
        }
    }

    private void settleUser(UUID userId, LocalDate date) {
        List<PortfolioItem> owned = portfolios.findAllByUserIdOrderBySortOrderAscCreatedAtAsc(userId);
        Map<String, Quote> latest = settlementQuotes(owned);
        var intraday = PerformanceCalculator.intraday(owned.stream().map(item -> {
            Quote quote = latest.get(item.canonical());
            return new PerformanceCalculator.PositionQuote(item.getQuantity(),
                    quote == null ? null : quote.last(),
                    quote == null ? null : quote.previousClose(), quote != null);
        }).toList());
        List<PerformanceCalculator.DailyReturn> history = daily
                .findAllByIdUserIdAndIdTradingDateBetweenOrderByIdTradingDateAsc(
                        userId, date.withDayOfYear(1), date.minusDays(1)).stream()
                .filter(value -> value.getDailyProfit() != null
                        && value.getDailyReturnPercent() != null
                        && value.getStatus() != PerformanceStatus.PARTIAL
                        && value.getStatus() != PerformanceStatus.UNAVAILABLE)
                .map(value -> new PerformanceCalculator.DailyReturn(
                        value.getId().getTradingDate(), value.getDailyProfit(),
                        value.getDailyReturnPercent())).collect(Collectors.toCollection(ArrayList::new));
        if (intraday.status() == PerformanceStatus.COMPLETE
                && intraday.dailyProfit() != null && intraday.dailyReturnPercent() != null) {
            history.add(new PerformanceCalculator.DailyReturn(date,
                    intraday.dailyProfit(), intraday.dailyReturnPercent()));
        }
        var year = PerformanceCalculator.yearToDate(date, history);
        UserPerformanceDaily record = daily.findById(new UserPerformanceDaily.Id(userId, date))
                .orElseGet(() -> new UserPerformanceDaily(userId, date));
        record.update(intraday, year, SETTLEMENT_SOURCE, Instant.now());
        daily.save(record);
        log.info("用户参考收益日终结算：userId={},date={},status={}",
                userId, date, record.getStatus());
    }

    private Map<String, Quote> settlementQuotes(List<PortfolioItem> owned) {
        if (owned.isEmpty()) return Map.of();
        try {
            return quotes.snapshots(owned.stream().map(item ->
                            InstrumentId.parse(item.canonical())).toList(),
                    new QuoteRequestOptions(MarketDataConfig.Mode.MARKET_SNAPSHOT,
                            MarketDataConfig.SnapshotSource.EASTMONEY, null))
                    .stream().collect(Collectors.toMap(Quote::instrumentId, value -> value,
                            (first, ignored) -> first));
        } catch (QuoteUnavailableException exception) {
            log.warn("统一结算行情暂不可用：error={}", exception.getClass().getSimpleName());
            return Map.of();
        }
    }
}
