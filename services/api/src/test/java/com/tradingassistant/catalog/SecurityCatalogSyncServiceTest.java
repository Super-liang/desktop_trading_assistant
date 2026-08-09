package com.tradingassistant.catalog;

import com.tradingassistant.marketdata.AkshareGatewayClient;
import com.tradingassistant.quote.InstrumentId;
import com.tradingassistant.market.AssetType;
import com.tradingassistant.market.Currency;
import com.tradingassistant.market.Exchange;
import com.tradingassistant.market.Market;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityCatalogSyncServiceTest {
    @Mock SecurityCatalogRepository repository;
    @Mock AkshareGatewayClient gateway;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> values;

    @Test
    void validatesAndUpsertsNonEmptyCatalog() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(gateway.catalog(Market.A_SHARE)).thenReturn(List.of(
                new AkshareGatewayClient.CatalogInstrument(
                        "SSE:600519", "600519", "贵州茅台", Market.A_SHARE,
                        Exchange.SSE, Currency.CNY, AssetType.STOCK, "600519")));
        when(repository.findAllByMarket(Market.A_SHARE)).thenReturn(List.of());
        SecurityCatalogSyncService service = new SecurityCatalogSyncService(repository, gateway, redis, 1);

        service.sync(Market.A_SHARE);

        verify(repository).saveAll(argThat(items -> items.iterator().hasNext()));
        verify(redis).execute(any(), anyList(), any());
    }

    @Test
    void emptyUpstreamNeverClearsExistingCatalog() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(gateway.catalog(Market.HK_STOCK)).thenReturn(List.of());
        SecurityCatalogSyncService service = new SecurityCatalogSyncService(repository, gateway, redis, 1);

        assertThatThrownBy(() -> service.sync(Market.HK_STOCK))
                .isInstanceOf(IllegalStateException.class);

        verify(repository, never()).deleteAll();
        verify(repository, never()).saveAll(any());
        verify(redis).execute(any(), anyList(), any());
    }

    @Test
    void successfulMarketSyncMarksOnlyMissingRowsInThatMarketInactive() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        SecurityCatalogItem missing = new SecurityCatalogItem(
                "HKEX:00005", "00005", "汇丰控股", Market.HK_STOCK, Exchange.HKEX,
                Currency.HKD, AssetType.STOCK, "5", "AKSHARE", Instant.EPOCH);
        when(repository.findAllByMarket(Market.HK_STOCK)).thenReturn(List.of(missing));
        when(gateway.catalog(Market.HK_STOCK)).thenReturn(List.of(
                new AkshareGatewayClient.CatalogInstrument(
                        "HKEX:00700", "00700", "腾讯控股", Market.HK_STOCK,
                        Exchange.HKEX, Currency.HKD, AssetType.STOCK, "700")));
        SecurityCatalogSyncService service = new SecurityCatalogSyncService(repository, gateway, redis, 1);

        service.sync(Market.HK_STOCK);

        assertThat(missing.getStatus()).isEqualTo(SecurityCatalogItem.Status.INACTIVE);
        verify(repository).saveAll(argThat(items -> {
            List<SecurityCatalogItem> saved = (List<SecurityCatalogItem>) items;
            return saved.size() == 2 && saved.stream().allMatch(
                    item -> item.getMarket() == Market.HK_STOCK);
        }));
    }
}
