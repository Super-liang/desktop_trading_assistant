package com.tradingassistant.catalog;

import com.tradingassistant.quote.InstrumentId;
import com.tradingassistant.market.Market;
import com.tradingassistant.market.AssetType;
import com.tradingassistant.market.Currency;
import com.tradingassistant.market.Exchange;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityCatalogServiceTest {
    @Mock SecurityCatalogRepository repository;

    @Test
    void searchesPostgresCatalogAndValidatesInstrument() {
        SecurityCatalogItem item = new SecurityCatalogItem(InstrumentId.parse("SSE:600519"),
                "贵州茅台", "AKSHARE", Instant.parse("2026-07-22T00:00:00Z"));
        when(repository.countByMarket(Market.A_SHARE)).thenReturn(1L);
        when(repository.searchByMarket(eq(Market.A_SHARE), eq("600"), any(Pageable.class)))
                .thenReturn(List.of(item));
        when(repository.findById("SSE:600519")).thenReturn(java.util.Optional.of(item));
        SecurityCatalogService service = new SecurityCatalogService(repository);

        assertThat(service.search(Market.A_SHARE, "600", 20)).singleElement()
                .extracting(SecurityCatalogService.View::name).isEqualTo("贵州茅台");
        assertThat(service.requireActive(InstrumentId.parse("600519"))).isSameAs(item);
    }

    @Test
    void emptyCatalogReturnsExplicitPreparingError() {
        when(repository.countByMarket(Market.A_SHARE)).thenReturn(0L);
        SecurityCatalogService service = new SecurityCatalogService(repository);

        assertThatThrownBy(() -> service.search(Market.A_SHARE, "600519", 20))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("准备中");
        verify(repository, never()).searchByMarket(any(), anyString(), any());
    }

    @Test
    void marketSearchCannotMixResultsAndReturnsCurrencyAndExchange() {
        SecurityCatalogItem hk = new SecurityCatalogItem(
                "HKEX:00700", "00700", "腾讯控股", Market.HK_STOCK, Exchange.HKEX,
                Currency.HKD, AssetType.STOCK, "700", "AKSHARE", Instant.EPOCH);
        when(repository.countByMarket(Market.HK_STOCK)).thenReturn(1L);
        when(repository.searchByMarket(eq(Market.HK_STOCK), eq("腾讯"), any(Pageable.class)))
                .thenReturn(List.of(hk));
        SecurityCatalogService service = new SecurityCatalogService(repository);

        SecurityCatalogService.View result = service.search(Market.HK_STOCK, "腾讯", 20).get(0);

        assertThat(result.market()).isEqualTo(Market.HK_STOCK);
        assertThat(result.exchange()).isEqualTo(Exchange.HKEX);
        assertThat(result.currency()).isEqualTo(Currency.HKD);
        verify(repository, never()).searchByMarket(eq(Market.A_SHARE), anyString(), any());
    }

    @Test
    void requireActiveRejectsInstrumentFromAnotherMarket() {
        SecurityCatalogItem hk = new SecurityCatalogItem(
                "HKEX:00700", "00700", "腾讯控股", Market.HK_STOCK, Exchange.HKEX,
                Currency.HKD, AssetType.STOCK, "700", "AKSHARE", Instant.EPOCH);
        when(repository.findById("HKEX:00700")).thenReturn(java.util.Optional.of(hk));
        SecurityCatalogService service = new SecurityCatalogService(repository);

        assertThatThrownBy(() -> service.requireActive(
                Market.US_STOCK, new com.tradingassistant.market.InstrumentKey(
                        Exchange.HKEX, "00700")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("所选市场");
    }
}
