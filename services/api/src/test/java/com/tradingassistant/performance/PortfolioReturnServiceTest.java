package com.tradingassistant.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.tradingassistant.catalog.FundNavQuoteRepository;
import com.tradingassistant.catalog.SecurityCatalogItem;
import com.tradingassistant.market.*;
import com.tradingassistant.marketdata.*;
import com.tradingassistant.portfolio.*;
import com.tradingassistant.quote.Quote;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortfolioReturnServiceTest {
    @Test
    void groupsReturnsByMarketAndCurrencyWithoutCrossCurrencyTotal() {
        var portfolios = mock(PortfolioRepository.class);
        var baselines = mock(PositionDailyBaselineRepository.class);
        var navs = mock(FundNavQuoteRepository.class);
        var snapshots = mock(RedisMarketSnapshotRepository.class);
        var configs = mock(MarketDataConfigService.class);
        UUID userId = UUID.randomUUID();
        PortfolioItem a = position(userId, Market.A_SHARE, Exchange.SSE, Currency.CNY,
                "600519", "贵州茅台");
        PortfolioItem hk = position(userId, Market.HK_STOCK, Exchange.HKEX, Currency.HKD,
                "00700", "腾讯控股");
        when(portfolios.findAllByUserIdOrderBySortOrderAscCreatedAtAsc(userId))
                .thenReturn(List.of(a, hk));
        MarketDataConfig config = MarketDataConfig.defaults();
        when(configs.current()).thenReturn(config);
        PositionDailyBaseline aBase = baseline(a, Market.A_SHARE);
        PositionDailyBaseline hkBase = baseline(hk, Market.HK_STOCK);
        when(baselines.findAllByUserIdAndTradingDate(eq(userId), any()))
                .thenReturn(List.of(aBase, hkBase));
        when(snapshots.find(eq(Market.A_SHARE), any(), anyList()))
                .thenReturn(List.of(quote(a, "12")));
        when(snapshots.find(eq(Market.HK_STOCK), any(), anyList()))
                .thenReturn(List.of(quote(hk, "12")));

        PortfolioReturns result = new PortfolioReturnService(portfolios, baselines, navs,
                snapshots, configs).current(userId);

        assertThat(result.groups()).extracting(PortfolioReturns.Group::currency)
                .containsExactly(Currency.CNY, Currency.HKD);
        assertThat(result.groups()).allSatisfy(group -> {
            assertThat(group.dailyProfit()).isEqualByComparingTo("20");
            assertThat(group.holdingProfit()).isEqualByComparingTo("20");
        });
    }

    @Test
    void editAfterOpeningBaselineMakesOnlyDailyReturnUnavailable() {
        var portfolios = mock(PortfolioRepository.class);
        var baselines = mock(PositionDailyBaselineRepository.class);
        var navs = mock(FundNavQuoteRepository.class);
        var snapshots = mock(RedisMarketSnapshotRepository.class);
        var configs = mock(MarketDataConfigService.class);
        UUID userId = UUID.randomUUID();
        PortfolioItem item = position(userId, Market.A_SHARE, Exchange.SSE, Currency.CNY,
                "600519", "贵州茅台");
        PositionDailyBaseline baseline = baseline(item, Market.A_SHARE);
        item.update(item.getDisplayName(), item.getOpenedOn(), bd("20"), bd("9"), 0);
        when(portfolios.findAllByUserIdOrderBySortOrderAscCreatedAtAsc(userId))
                .thenReturn(List.of(item));
        when(baselines.findAllByUserIdAndTradingDate(eq(userId), any()))
                .thenReturn(List.of(baseline));
        when(configs.current()).thenReturn(MarketDataConfig.defaults());
        when(snapshots.find(eq(Market.A_SHARE), any(), anyList()))
                .thenReturn(List.of(quote(item, "12")));

        var result = new PortfolioReturnService(portfolios, baselines, navs, snapshots, configs)
                .current(userId).groups().get(0).items().get(0);
        assertThat(result.dailyStatus()).isEqualTo(PerformanceStatus.UNAVAILABLE);
        assertThat(result.unavailableReason()).isEqualTo("MUTATED_AFTER_OPEN");
        assertThat(result.holdingProfit()).isEqualByComparingTo("60");
    }

    private PortfolioItem position(UUID userId, Market market, Exchange exchange,
            Currency currency, String code, String name) {
        Instant now = Instant.now();
        var security = new SecurityCatalogItem(exchange + ":" + code, code, name, market,
                exchange, currency, AssetType.STOCK, code, "AKSHARE", now);
        return new PortfolioItem(userId, security, LocalDate.now(market.timezone()).minusDays(2),
                bd("10"), bd("10"), 0);
    }

    private PositionDailyBaseline baseline(PortfolioItem item, Market market) {
        var baseline = new PositionDailyBaseline(item.getId(), item.getUserId(), market,
                LocalDate.now(market.timezone()), item.getCurrency());
        baseline.complete(bd("10"), bd("10"), "EASTMONEY", item.getUpdatedAt());
        return baseline;
    }

    private Quote quote(PortfolioItem item, String last) {
        Instant now = Instant.now();
        return new Quote(item.canonical(), item.getDisplayName(), bd(last), bd("10"), bd("10"),
                bd(last), bd("9"), bd("2"), bd("20"), bd("100"), "OPEN", "EASTMONEY",
                now, now, false, false, false);
    }

    private BigDecimal bd(String value) { return new BigDecimal(value); }
}
