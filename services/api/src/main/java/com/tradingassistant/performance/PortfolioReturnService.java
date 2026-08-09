package com.tradingassistant.performance;

import com.tradingassistant.catalog.FundNavQuote;
import com.tradingassistant.catalog.FundNavQuoteRepository;
import com.tradingassistant.market.Market;
import com.tradingassistant.marketdata.MarketDataConfig;
import com.tradingassistant.marketdata.MarketDataConfigService;
import com.tradingassistant.marketdata.RedisMarketSnapshotRepository;
import com.tradingassistant.portfolio.PortfolioItem;
import com.tradingassistant.portfolio.PortfolioRepository;
import com.tradingassistant.quote.Quote;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PortfolioReturnService {
    private final PortfolioRepository portfolios;
    private final PositionDailyBaselineRepository baselines;
    private final FundNavQuoteRepository fundNavs;
    private final RedisMarketSnapshotRepository snapshots;
    private final MarketDataConfigService config;

    public PortfolioReturnService(PortfolioRepository portfolios,
            PositionDailyBaselineRepository baselines, FundNavQuoteRepository fundNavs,
            RedisMarketSnapshotRepository snapshots, MarketDataConfigService config) {
        this.portfolios = portfolios;
        this.baselines = baselines;
        this.fundNavs = fundNavs;
        this.snapshots = snapshots;
        this.config = config;
    }

    public PortfolioReturns current(UUID userId) {
        Instant now = Instant.now();
        List<PortfolioItem> owned = portfolios.findAllByUserIdOrderBySortOrderAscCreatedAtAsc(userId);
        List<PortfolioReturns.Group> groups = Arrays.stream(Market.values())
                .map(market -> group(userId, market,
                        owned.stream().filter(item -> item.getMarket() == market).toList()))
                .filter(Objects::nonNull).toList();
        return new PortfolioReturns(groups, now, PortfolioReturns.NOTICE);
    }

    private PortfolioReturns.Group group(UUID userId, Market market, List<PortfolioItem> owned) {
        if (owned.isEmpty()) return null;
        List<PortfolioReturns.Item> items = market == Market.PUBLIC_FUND
                ? fundItems(owned) : stockItems(userId, market, owned);
        List<PortfolioReturns.Item> daily = items.stream()
                .filter(item -> item.dailyStatus() == PerformanceStatus.COMPLETE).toList();
        List<PortfolioReturns.Item> holding = items.stream()
                .filter(item -> item.holdingProfit() != null).toList();
        BigDecimal dailyProfit = sum(daily, PortfolioReturns.Item::dailyProfit);
        BigDecimal dailyRate = aggregateRate(daily, PortfolioReturns.Item::dailyProfit,
                PortfolioReturns.Item::dailyBaseValue);
        BigDecimal holdingProfit = sum(holding, PortfolioReturns.Item::holdingProfit);
        BigDecimal holdingRate = aggregateRate(holding, PortfolioReturns.Item::holdingProfit,
                PortfolioReturns.Item::holdingBaseValue);
        int unavailable = owned.size() - daily.size();
        PerformanceStatus status = daily.isEmpty() ? PerformanceStatus.UNAVAILABLE
                : unavailable == 0 ? PerformanceStatus.COMPLETE : PerformanceStatus.PARTIAL;
        return new PortfolioReturns.Group(market, market.currency(), dailyProfit, dailyRate,
                holdingProfit, holdingRate, status, unavailable, items);
    }

    private List<PortfolioReturns.Item> stockItems(UUID userId, Market market,
            List<PortfolioItem> owned) {
        LocalDate date = LocalDate.now(market.timezone());
        Map<UUID, PositionDailyBaseline> baselineMap = baselines
                .findAllByUserIdAndTradingDate(userId, date).stream()
                .collect(Collectors.toMap(PositionDailyBaseline::getPositionId,
                        Function.identity(), (first, ignored) -> first));
        Map<String, Quote> quoteMap = stockQuotes(market, owned);
        return owned.stream().map(position -> {
            Quote quote = quoteMap.get(position.canonical());
            PositionDailyBaseline baseline = baselineMap.get(position.getId());
            boolean unchanged = baseline != null && baseline.getCapturedAt() != null
                    && !position.getUpdatedAt().isAfter(baseline.getCapturedAt());
            boolean dailyAvailable = baseline != null
                    && baseline.getStatus() == PositionDailyBaseline.Status.COMPLETE && unchanged;
            var result = PortfolioReturnCalculator.stock(position.getQuantity(),
                    position.getCostPrice(), quote == null ? null : quote.last(),
                    baseline == null ? null : baseline.getOpeningQuantity(),
                    baseline == null ? null : baseline.getOpeningPrice(), dailyAvailable);
            String reason = dailyAvailable ? null : baselineReason(baseline, unchanged);
            return item(position, quote == null ? null : quote.last(), date,
                    quote == null ? null : quote.sourceTimestamp(),
                    quote != null && quote.delayed(), quote == null || quote.stale(), result, reason);
        }).toList();
    }

    private Map<String, Quote> stockQuotes(Market market, List<PortfolioItem> owned) {
        try {
            return snapshots.find(market, MarketDataConfig.SnapshotSource.SINA,
                            owned.stream().map(PortfolioItem::canonical).toList())
                    .stream().collect(Collectors.toMap(Quote::instrumentId, Function.identity(),
                            (first, ignored) -> first));
        } catch (RuntimeException exception) {
            return Map.of();
        }
    }

    private List<PortfolioReturns.Item> fundItems(List<PortfolioItem> owned) {
        return owned.stream().map(position -> {
            List<FundNavQuote> navs = fundNavs
                    .findTop2ByInstrumentIdOrderByNavDateDesc(position.canonical());
            FundNavQuote current = navs.isEmpty() ? null : navs.get(0);
            FundNavQuote previous = navs.size() < 2 ? null : navs.get(1);
            var result = PortfolioReturnCalculator.fund(position.getQuantity(),
                    position.getCostPrice(), current == null ? null : current.getUnitNav(),
                    previous == null ? null : previous.getUnitNav(), position.getOpenedOn(),
                    current == null ? null : current.getNavDate());
            String reason = result.daily().available() ? null
                    : current == null ? "MISSING_NAV" : previous == null ? "MISSING_PREVIOUS_NAV"
                    : "OPENED_ON_VALUE_DATE";
            return item(position, current == null ? null : current.getUnitNav(),
                    current == null ? null : current.getNavDate(), null, true,
                    current == null, result, reason);
        }).toList();
    }

    private PortfolioReturns.Item item(PortfolioItem position, BigDecimal price,
            LocalDate valueDate, Instant quoteAsOf, boolean delayed, boolean stale,
            PortfolioReturnCalculator.Result result, String reason) {
        return new PortfolioReturns.Item(position.getId(), position.canonical(),
                position.getDisplayName(), position.getMarket(), position.getCurrency(), price,
                valueDate, quoteAsOf, delayed, stale, result.daily().profit(),
                result.daily().returnPercent(), result.holding().profit(),
                result.holding().returnPercent(), result.daily().available()
                        ? PerformanceStatus.COMPLETE : PerformanceStatus.UNAVAILABLE,
                reason, result.daily().baseValue(), result.holding().baseValue());
    }

    private String baselineReason(PositionDailyBaseline baseline, boolean unchanged) {
        if (baseline == null) return "MISSING_BASELINE";
        if (!unchanged) return PositionDailyBaseline.Reason.MUTATED_AFTER_OPEN.name();
        return baseline.getStatusReason() == null ? "MISSING_BASELINE"
                : baseline.getStatusReason().name();
    }

    private BigDecimal sum(List<PortfolioReturns.Item> items,
            Function<PortfolioReturns.Item, BigDecimal> getter) {
        return items.isEmpty() ? null : items.stream().map(getter)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal aggregateRate(List<PortfolioReturns.Item> items,
            Function<PortfolioReturns.Item, BigDecimal> profit,
            Function<PortfolioReturns.Item, BigDecimal> base) {
        if (items.isEmpty()) return null;
        BigDecimal denominator = items.stream().map(base).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (denominator.signum() <= 0) return null;
        BigDecimal numerator = items.stream().map(profit).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return numerator.divide(denominator, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(6, RoundingMode.HALF_UP);
    }
}
