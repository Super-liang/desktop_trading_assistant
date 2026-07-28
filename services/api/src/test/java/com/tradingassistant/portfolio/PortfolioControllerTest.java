package com.tradingassistant.portfolio;

import com.tradingassistant.catalog.SecurityCatalogItem;
import com.tradingassistant.catalog.SecurityCatalogService;
import com.tradingassistant.audit.UserOperationAuditService;
import com.tradingassistant.audit.UserOperationAudit;
import com.tradingassistant.quote.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioControllerTest {
    @Mock PortfolioRepository repository;
    @Mock QuoteProviderRegistry quotes;
    @Mock SecurityCatalogService catalog;
    @Mock UserOperationAuditService audits;
    @Mock Jwt jwt;

    @Test
    void createsWatchlistItemWithoutCallingQuoteProvider() {
        UUID userId = UUID.randomUUID();
        InstrumentId instrument = InstrumentId.parse("SSE:600519");
        when(jwt.getSubject()).thenReturn(userId.toString());
        when(catalog.requireActive(instrument)).thenReturn(new SecurityCatalogItem(
                instrument, "贵州茅台", "AKSHARE", Instant.now()));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PortfolioController controller = new PortfolioController(repository, quotes, catalog, audits);

        PortfolioController.ItemView result = controller.create(jwt,
                new PortfolioController.ItemRequest("SSE:600519", "任意名称",
                        BigDecimal.ZERO, null, 0));

        assertThat(result.displayName()).isEqualTo("贵州茅台");
        assertThat(result.quote()).isNull();
        assertThat(result.costPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        verifyNoInteractions(quotes);
        verify(audits).record(eq(userId),
                eq(UserOperationAudit.Action.PORTFOLIO_CREATED), eq(result.id()),
                eq("SSE:600519"), eq("贵州茅台"), eq(UserOperationAudit.Result.SUCCESS));
    }

    @Test
    void missingQuoteForOneInstrumentDoesNotFailPortfolioList() {
        UUID userId = UUID.randomUUID();
        when(jwt.getSubject()).thenReturn(userId.toString());
        PortfolioItem first = new PortfolioItem(userId, InstrumentId.parse("SSE:600519"),
                "贵州茅台", BigDecimal.ONE, BigDecimal.TEN, 0);
        PortfolioItem second = new PortfolioItem(userId, InstrumentId.parse("SZSE:000001"),
                "平安银行", BigDecimal.ZERO, BigDecimal.ZERO, 1);
        when(repository.findAllByUserIdOrderBySortOrderAscCreatedAtAsc(userId))
                .thenReturn(List.of(first, second));
        Instant now = Instant.now();
        when(quotes.snapshots(anyList(), any())).thenReturn(List.of(new Quote("SSE:600519", "贵州茅台",
                new BigDecimal("11"), BigDecimal.TEN, BigDecimal.TEN, new BigDecimal("11"),
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, "CONTINUOUS",
                "AKSHARE_SINA_SNAPSHOT", now, now, true, false, false)));
        PortfolioController controller = new PortfolioController(repository, quotes, catalog, audits);

        PortfolioController.PortfolioSummary result = controller.list(
                jwt, com.tradingassistant.marketdata.MarketDataConfig.Mode.MARKET_SNAPSHOT,
                com.tradingassistant.marketdata.MarketDataConfig.SnapshotSource.SINA,
                com.tradingassistant.marketdata.MarketDataConfig.SingleSource.XUEQIU);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).quote()).isNotNull();
        assertThat(result.items().get(1).quote()).isNull();
        assertThat(result.items().get(1).marketValue()).isNull();
        assertThat(result.unavailableQuoteCount()).isEqualTo(1);
        verify(quotes).snapshots(anyList(), argThat(options ->
                options.mode() == com.tradingassistant.marketdata.MarketDataConfig.Mode.MARKET_SNAPSHOT
                && options.snapshotSource()
                        == com.tradingassistant.marketdata.MarketDataConfig.SnapshotSource.SINA
                && options.singleSource()
                        == com.tradingassistant.marketdata.MarketDataConfig.SingleSource.XUEQIU));
    }

    @Test
    void updateAndDeleteWriteSafeOperationAudits() {
        UUID userId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        InstrumentId instrument = InstrumentId.parse("SSE:600519");
        PortfolioItem item = new PortfolioItem(userId, instrument, "贵州茅台",
                BigDecimal.TEN, BigDecimal.ONE, 0);
        when(jwt.getSubject()).thenReturn(userId.toString());
        when(repository.findByIdAndUserId(itemId, userId)).thenReturn(java.util.Optional.of(item));
        when(catalog.requireActive(instrument)).thenReturn(new SecurityCatalogItem(
                instrument, "贵州茅台", "AKSHARE", Instant.now()));
        PortfolioController controller = new PortfolioController(repository, quotes, catalog, audits);

        controller.update(jwt, itemId, new PortfolioController.ItemRequest(
                "SSE:600519", "忽略名称", BigDecimal.ONE, BigDecimal.ONE, 0));
        controller.delete(jwt, itemId);

        verify(audits).record(eq(userId), eq(UserOperationAudit.Action.PORTFOLIO_UPDATED),
                eq(item.getId()), eq("SSE:600519"), eq("贵州茅台"),
                eq(UserOperationAudit.Result.SUCCESS));
        verify(audits).record(eq(userId), eq(UserOperationAudit.Action.PORTFOLIO_DELETED),
                eq(item.getId()), eq("SSE:600519"), eq("贵州茅台"),
                eq(UserOperationAudit.Result.SUCCESS));
    }
}
