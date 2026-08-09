package com.tradingassistant.catalog;

import com.tradingassistant.market.AssetType;
import com.tradingassistant.market.Currency;
import com.tradingassistant.market.Exchange;
import com.tradingassistant.market.Market;
import com.tradingassistant.marketdata.AkshareGatewayClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FundNavSyncServiceTest {
    @Mock SecurityCatalogRepository catalog;
    @Mock FundNavQuoteRepository navQuotes;
    @Mock AkshareGatewayClient gateway;

    @Test
    void storesLatestAndPreviousUnitNavWithIdempotentKeys() {
        SecurityCatalogItem fund = new SecurityCatalogItem(
                "CN_FUND:000001", "000001", "开放基金", Market.PUBLIC_FUND,
                Exchange.CN_FUND, Currency.CNY, AssetType.OPEN_END_FUND, "000001",
                "AKSHARE", Instant.EPOCH);
        when(catalog.findAllByMarketAndStatus(
                Market.PUBLIC_FUND, SecurityCatalogItem.Status.ACTIVE)).thenReturn(List.of(fund));
        when(gateway.allFundUnitNav()).thenReturn(List.of(new AkshareGatewayClient.FundNav(
                "CN_FUND:000001", "000001", "开放基金", new BigDecimal("1.25"),
                LocalDate.of(2026, 7, 29), new BigDecimal("1.20"),
                LocalDate.of(2026, 7, 28), "AKSHARE_EASTMONEY_UNIT_NAV", Instant.EPOCH)));
        FundNavSyncService service = new FundNavSyncService(catalog, navQuotes, gateway);

        service.synchronize();
        service.synchronize();

        verify(navQuotes, times(2)).saveAll(argThat(rows -> {
            List<FundNavQuote> values = (List<FundNavQuote>) rows;
            return values.size() == 2
                    && values.get(0).getNavDate().equals(LocalDate.of(2026, 7, 29))
                    && values.get(1).getNavDate().equals(LocalDate.of(2026, 7, 28));
        }));
    }

    @Test
    void upstreamFailureNeverDeletesHistoricalNav() {
        SecurityCatalogItem fund = new SecurityCatalogItem(
                "CN_FUND:000001", "000001", "开放基金", Market.PUBLIC_FUND,
                Exchange.CN_FUND, Currency.CNY, AssetType.OPEN_END_FUND, "000001",
                "AKSHARE", Instant.EPOCH);
        when(catalog.findAllByMarketAndStatus(any(), any())).thenReturn(List.of(fund));
        when(gateway.allFundUnitNav()).thenThrow(new IllegalStateException("upstream down"));
        FundNavSyncService service = new FundNavSyncService(catalog, navQuotes, gateway);

        assertThatThrownBy(service::synchronize).isInstanceOf(IllegalStateException.class);

        verify(navQuotes, never()).deleteAll();
        verify(navQuotes, never()).saveAll(any());
    }
}
