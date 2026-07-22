package com.tradingassistant.catalog;

import com.tradingassistant.quote.InstrumentId;
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
        when(repository.count()).thenReturn(1L);
        when(repository.search(eq("600"), any(Pageable.class))).thenReturn(List.of(item));
        when(repository.findById("SSE:600519")).thenReturn(java.util.Optional.of(item));
        SecurityCatalogService service = new SecurityCatalogService(repository);

        assertThat(service.search("600", 20)).singleElement()
                .extracting(SecurityCatalogService.View::name).isEqualTo("贵州茅台");
        assertThat(service.requireActive(InstrumentId.parse("600519"))).isSameAs(item);
    }

    @Test
    void emptyCatalogReturnsExplicitPreparingError() {
        when(repository.count()).thenReturn(0L);
        SecurityCatalogService service = new SecurityCatalogService(repository);

        assertThatThrownBy(() -> service.search("600519", 20))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("准备中");
        verify(repository, never()).search(anyString(), any());
    }
}
