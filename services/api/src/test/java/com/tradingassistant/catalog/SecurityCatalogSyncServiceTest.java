package com.tradingassistant.catalog;

import com.tradingassistant.marketdata.AkshareGatewayClient;
import com.tradingassistant.quote.InstrumentId;
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
        when(gateway.catalog()).thenReturn(List.of(new AkshareGatewayClient.CatalogInstrument(
                "SSE:600519", "600519", "贵州茅台", InstrumentId.Exchange.SSE,
                InstrumentId.AssetType.STOCK)));
        when(repository.findAll()).thenReturn(List.of());
        SecurityCatalogSyncService service = new SecurityCatalogSyncService(repository, gateway, redis, 1);

        service.sync();

        verify(repository).saveAll(argThat(items -> items.iterator().hasNext()));
        verify(redis).execute(any(), anyList(), any());
    }

    @Test
    void emptyUpstreamNeverClearsExistingCatalog() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(gateway.catalog()).thenReturn(List.of());
        SecurityCatalogSyncService service = new SecurityCatalogSyncService(repository, gateway, redis, 1);

        assertThatThrownBy(service::sync).isInstanceOf(IllegalStateException.class);

        verify(repository, never()).deleteAll();
        verify(repository, never()).saveAll(any());
        verify(redis).execute(any(), anyList(), any());
    }
}
