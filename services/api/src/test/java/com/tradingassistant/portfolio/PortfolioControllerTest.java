package com.tradingassistant.portfolio;

import com.tradingassistant.catalog.SecurityCatalogItem;
import com.tradingassistant.catalog.SecurityCatalogService;
import com.tradingassistant.audit.UserOperationAuditService;
import com.tradingassistant.audit.UserOperationAudit;
import com.tradingassistant.quote.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import com.tradingassistant.market.*;
import com.tradingassistant.marketdata.MarketDataConfig;
import com.tradingassistant.marketdata.RedisMarketSnapshotRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import jakarta.validation.Validation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioControllerTest {
    @Mock PortfolioRepository repository;
    @Mock QuoteProviderRegistry quotes;
    @Mock SecurityCatalogService catalog;
    @Mock UserOperationAuditService audits;
    @Mock RedisMarketSnapshotRepository snapshots;
    @Mock Jwt jwt;

    @Test
    void createsWatchlistItemWithoutCallingQuoteProvider() {
        UUID userId = UUID.randomUUID();
        InstrumentId instrument = InstrumentId.parse("SSE:600519");
        when(jwt.getSubject()).thenReturn(userId.toString());
        when(catalog.requireActive(eq(Market.A_SHARE), any(InstrumentKey.class))).thenReturn(new SecurityCatalogItem(
                instrument, "贵州茅台", "AKSHARE", Instant.now()));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PortfolioController controller = new PortfolioController(repository, quotes, catalog, audits,
                snapshots);

        PortfolioController.ItemView result = controller.create(jwt,
                new PortfolioController.ItemRequest("SSE:600519", "任意名称", null, null,
                        BigDecimal.ZERO, null, 0));

        assertThat(result.displayName()).isEqualTo("贵州茅台");
        assertThat(result.quote()).isNull();
        assertThat(result.costPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        verifyNoInteractions(quotes);
        verify(audits).record(eq(userId),
                eq(UserOperationAudit.Action.PORTFOLIO_CREATED), eq(result.id()),
                eq("SSE:600519"), eq("贵州茅台"), eq(Market.A_SHARE),
                eq(result.openedOn()), eq(UserOperationAudit.Result.SUCCESS));
    }

    @Test
    void duplicateCreateReturnsExistingPositionConflictWithoutSaving() {
        UUID userId = UUID.randomUUID();
        InstrumentId instrument = InstrumentId.parse("SSE:600519");
        PortfolioItem existing = new PortfolioItem(userId, instrument, "贵州茅台",
                BigDecimal.TEN, BigDecimal.ONE, 0);
        when(jwt.getSubject()).thenReturn(userId.toString());
        when(catalog.requireActive(eq(Market.A_SHARE), any(InstrumentKey.class))).thenReturn(
                new SecurityCatalogItem(instrument, "贵州茅台", "AKSHARE", Instant.now()));
        when(repository.findByUserIdAndExchangeAndSymbolAndAssetType(
                userId, Exchange.SSE, "600519", AssetType.STOCK))
                .thenReturn(java.util.Optional.of(existing));
        PortfolioController controller = new PortfolioController(repository, quotes, catalog, audits,
                snapshots);

        assertThatThrownBy(() -> controller.create(jwt, new PortfolioController.ItemRequest(
                "SSE:600519", "贵州茅台", Market.A_SHARE, LocalDate.now(),
                BigDecimal.ONE, BigDecimal.TEN, 0)))
                .isInstanceOf(PositionAlreadyExistsException.class)
                .satisfies(error -> assertThat(((PositionAlreadyExistsException) error)
                        .existingPositionId()).isEqualTo(existing.getId()));
        verify(repository).lockUser(userId);
        verify(repository, never()).save(any());
    }

    @Test
    void accumulateUsesWeightedAverageAndPreservesOpenedDate() {
        UUID userId = UUID.randomUUID();
        UUID requestedId = UUID.randomUUID();
        LocalDate openedOn = LocalDate.now().minusDays(20);
        PortfolioItem existing = new PortfolioItem(userId, new SecurityCatalogItem(
                "SSE:600519", "600519", "贵州茅台", Market.A_SHARE, Exchange.SSE,
                Currency.CNY, AssetType.STOCK, "600519", "AKSHARE", Instant.now()),
                openedOn, new BigDecimal("100"), new BigDecimal("10"), 3);
        when(jwt.getSubject()).thenReturn(userId.toString());
        when(repository.findLockedByIdAndUserId(requestedId, userId))
                .thenReturn(java.util.Optional.of(existing));
        PortfolioController controller = new PortfolioController(repository, quotes, catalog, audits,
                snapshots);

        var result = controller.accumulate(jwt, requestedId,
                new PortfolioController.AccumulateRequest(new BigDecimal("100"),
                        new BigDecimal("20")));

        assertThat(result.quantity()).isEqualByComparingTo("200");
        assertThat(result.costPrice()).isEqualByComparingTo("15");
        assertThat(result.openedOn()).isEqualTo(openedOn);
        assertThat(result.sortOrder()).isEqualTo(3);
        verify(audits).record(eq(userId), eq(UserOperationAudit.Action.PORTFOLIO_UPDATED),
                eq(existing.getId()), eq("SSE:600519"), eq("贵州茅台"), eq(Market.A_SHARE),
                eq(openedOn), eq(UserOperationAudit.Result.SUCCESS));
    }

    @Test
    void accumulateZeroPositionAdoptsNewCostAndRejectsInvalidOrForeignPosition() {
        UUID userId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        PortfolioItem watchOnly = new PortfolioItem(userId, InstrumentId.parse("SSE:600519"),
                "贵州茅台", BigDecimal.ZERO, BigDecimal.ZERO, 0);
        when(jwt.getSubject()).thenReturn(userId.toString());
        when(repository.findLockedByIdAndUserId(itemId, userId))
                .thenReturn(java.util.Optional.of(watchOnly));
        PortfolioController controller = new PortfolioController(repository, quotes, catalog, audits,
                snapshots);

        var result = controller.accumulate(jwt, itemId,
                new PortfolioController.AccumulateRequest(BigDecimal.TEN, new BigDecimal("12.5")));

        assertThat(result.quantity()).isEqualByComparingTo("10");
        assertThat(result.costPrice()).isEqualByComparingTo("12.5");
        assertThatThrownBy(() -> controller.accumulate(jwt, itemId,
                new PortfolioController.AccumulateRequest(BigDecimal.ZERO, BigDecimal.ONE)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.accumulate(jwt, UUID.randomUUID(),
                new PortfolioController.AccumulateRequest(BigDecimal.ONE, BigDecimal.ONE)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
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
        PortfolioController controller = new PortfolioController(repository, quotes, catalog, audits,
                snapshots);

        PortfolioController.PortfolioSummary result = controller.list(
                jwt, null, com.tradingassistant.marketdata.MarketDataConfig.Mode.MARKET_SNAPSHOT,
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
        when(catalog.requireActive(eq(Market.A_SHARE), any(InstrumentKey.class))).thenReturn(new SecurityCatalogItem(
                instrument, "贵州茅台", "AKSHARE", Instant.now()));
        PortfolioController controller = new PortfolioController(repository, quotes, catalog, audits,
                snapshots);

        controller.update(jwt, itemId, new PortfolioController.ItemRequest(
                "SSE:600519", "忽略名称", null, null, BigDecimal.ONE, BigDecimal.ONE, 0));
        controller.delete(jwt, itemId);

        verify(audits).record(eq(userId), eq(UserOperationAudit.Action.PORTFOLIO_UPDATED),
                eq(item.getId()), eq("SSE:600519"), eq("贵州茅台"),
                eq(Market.A_SHARE), eq(item.getOpenedOn()), eq(UserOperationAudit.Result.SUCCESS));
        verify(audits).record(eq(userId), eq(UserOperationAudit.Action.PORTFOLIO_DELETED),
                eq(item.getId()), eq("SSE:600519"), eq("贵州茅台"),
                eq(Market.A_SHARE), eq(item.getOpenedOn()), eq(UserOperationAudit.Result.SUCCESS));
    }

    @Test
    void createsHongKongPositionFromCatalogAndRejectsFutureOpenedDate() {
        UUID userId = UUID.randomUUID();
        when(jwt.getSubject()).thenReturn(userId.toString());
        SecurityCatalogItem security = new SecurityCatalogItem(
                "HKEX:00700", "00700", "腾讯控股", Market.HK_STOCK, Exchange.HKEX,
                Currency.HKD, AssetType.STOCK, "700", "AKSHARE", Instant.now());
        when(catalog.requireActive(Market.HK_STOCK, new InstrumentKey(Exchange.HKEX, "00700")))
                .thenReturn(security);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PortfolioController controller = new PortfolioController(repository, quotes, catalog, audits,
                snapshots);

        PortfolioController.ItemView result = controller.create(jwt,
                new PortfolioController.ItemRequest("HKEX:00700", "伪造名称", Market.HK_STOCK,
                        LocalDate.now().minusDays(1), BigDecimal.ONE,
                        new BigDecimal("300.25"), 0));

        assertThat(result.displayName()).isEqualTo("腾讯控股");
        assertThat(result.currency()).isEqualTo(Currency.HKD);
        assertThat(result.market()).isEqualTo(Market.HK_STOCK);
        assertThatThrownBy(() -> controller.create(jwt,
                new PortfolioController.ItemRequest("HKEX:00700", "腾讯控股", Market.HK_STOCK,
                        LocalDate.now().plusDays(1), BigDecimal.ONE, BigDecimal.ONE, 0)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("建仓日期");
    }

    @Test
    void fundUnitsRejectMoreThanFourDecimalPlaces() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        var request = new PortfolioController.ItemRequest(
                "CN_FUND:000001", "开放基金", Market.PUBLIC_FUND,
                LocalDate.now().minusDays(1), new BigDecimal("1.12345"),
                new BigDecimal("1.234567"), 0);

        assertThat(validator.validate(request)).anyMatch(violation ->
                violation.getPropertyPath().toString().equals("quantity"));
    }

    @Test
    void listFiltersByRequestedMarketAndKeepsEmptyMarketVisible() {
        UUID userId = UUID.randomUUID();
        when(jwt.getSubject()).thenReturn(userId.toString());
        when(repository.findAllByUserIdAndMarketOrderBySortOrderAscCreatedAtAsc(
                userId, Market.US_STOCK)).thenReturn(List.of());
        PortfolioController controller = new PortfolioController(repository, quotes, catalog, audits,
                snapshots);

        PortfolioController.PortfolioSummary result = controller.list(
                jwt, Market.US_STOCK, null, null, null);

        assertThat(result.items()).isEmpty();
        verify(repository, never()).findAllByUserIdOrderBySortOrderAscCreatedAtAsc(any());
        verifyNoInteractions(quotes);
    }

    @Test
    void crossMarketListReadsEachMarketFromSinaSnapshot() {
        UUID userId = UUID.randomUUID();
        when(jwt.getSubject()).thenReturn(userId.toString());
        PortfolioItem hk = new PortfolioItem(userId, new SecurityCatalogItem(
                "HKEX:00700", "00700", "腾讯控股", Market.HK_STOCK, Exchange.HKEX,
                Currency.HKD, AssetType.STOCK, "700", "AKSHARE", Instant.now()),
                LocalDate.now().minusDays(1), BigDecimal.ONE, BigDecimal.TEN, 0);
        PortfolioItem us = new PortfolioItem(userId, new SecurityCatalogItem(
                "NASDAQ:AAPL", "AAPL", "Apple", Market.US_STOCK, Exchange.NASDAQ,
                Currency.USD, AssetType.STOCK, "AAPL", "AKSHARE", Instant.now()),
                LocalDate.now().minusDays(1), BigDecimal.ONE, BigDecimal.TEN, 1);
        when(repository.findAllByUserIdOrderBySortOrderAscCreatedAtAsc(userId))
                .thenReturn(List.of(hk, us));
        Instant now = Instant.now();
        Quote hkQuote = new Quote("HKEX:00700", "腾讯控股", BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ONE, "OPEN", "AKSHARE_SINA_SNAPSHOT",
                now, now, true, false, false);
        Quote usQuote = new Quote("NASDAQ:AAPL", "Apple", BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ONE, "OPEN", "AKSHARE_SINA_SNAPSHOT",
                now, now, true, false, false);
        when(snapshots.find(Market.HK_STOCK, MarketDataConfig.SnapshotSource.SINA,
                List.of("HKEX:00700"))).thenReturn(List.of(hkQuote));
        when(snapshots.find(Market.US_STOCK, MarketDataConfig.SnapshotSource.SINA,
                List.of("NASDAQ:AAPL"))).thenReturn(List.of(usQuote));
        PortfolioController controller = new PortfolioController(repository, quotes, catalog, audits,
                snapshots);

        var result = controller.list(jwt, null, MarketDataConfig.Mode.MARKET_SNAPSHOT,
                MarketDataConfig.SnapshotSource.EASTMONEY, null);

        assertThat(result.items()).extracting(item -> item.quote().instrumentId())
                .containsExactly("HKEX:00700", "NASDAQ:AAPL");
        verifyNoInteractions(quotes);
    }
}
