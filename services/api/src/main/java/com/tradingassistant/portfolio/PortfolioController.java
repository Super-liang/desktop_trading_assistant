package com.tradingassistant.portfolio;

import com.tradingassistant.catalog.SecurityCatalogItem;
import com.tradingassistant.catalog.SecurityCatalogService;
import com.tradingassistant.audit.UserOperationAudit;
import com.tradingassistant.audit.UserOperationAuditService;
import com.tradingassistant.marketdata.MarketDataConfig;
import com.tradingassistant.marketdata.RedisMarketSnapshotRepository;
import com.tradingassistant.market.Exchange;
import com.tradingassistant.market.InstrumentKey;
import com.tradingassistant.market.Market;
import com.tradingassistant.quote.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/portfolio/items")
public class PortfolioController {
    private final PortfolioRepository items;
    private final QuoteProviderRegistry quotes;
    private final SecurityCatalogService catalog;
    private final UserOperationAuditService audits;
    private final RedisMarketSnapshotRepository snapshots;

    public PortfolioController(PortfolioRepository items, QuoteProviderRegistry quotes,
            SecurityCatalogService catalog, UserOperationAuditService audits,
            RedisMarketSnapshotRepository snapshots) {
        this.items = items;
        this.quotes = quotes;
        this.catalog = catalog;
        this.audits = audits;
        this.snapshots = snapshots;
    }

    @GetMapping
    PortfolioSummary list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Market market,
            @RequestParam(required = false) MarketDataConfig.Mode mode,
            @RequestParam(required = false) MarketDataConfig.SnapshotSource snapshotSource,
            @RequestParam(required = false) MarketDataConfig.SingleSource singleSource) {
        List<PortfolioItem> owned = market == null
                ? items.findAllByUserIdOrderBySortOrderAscCreatedAtAsc(userId(jwt))
                : items.findAllByUserIdAndMarketOrderBySortOrderAscCreatedAtAsc(userId(jwt), market);
        Map<String, Quote> latest = latestQuotes(owned, mode, snapshotSource, singleSource);
        List<ItemView> views = owned.stream().map(item -> view(item, latest.get(item.canonical()))).toList();
        BigDecimal marketValue = views.stream().map(ItemView::marketValue).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal profit = views.stream().map(ItemView::profit).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int unavailable = (int) views.stream().filter(view -> view.quote() == null).count();
        return new PortfolioSummary(views, marketValue, profit, unavailable,
                "不含手续费、税费、分红送转影响；合计仅包含有行情的证券");
    }

    private Map<String, Quote> latestQuotes(List<PortfolioItem> owned,
            MarketDataConfig.Mode mode,
            MarketDataConfig.SnapshotSource snapshotSource,
            MarketDataConfig.SingleSource singleSource) {
        Map<String, Quote> result = new HashMap<>();
        List<InstrumentId> aShareInstruments = owned.stream()
                .filter(item -> item.getMarket() == Market.A_SHARE)
                .map(item -> InstrumentId.parse(item.canonical())).toList();
        if (!aShareInstruments.isEmpty()) {
            try {
                MarketDataConfig.SnapshotSource normalized = mode == MarketDataConfig.Mode.SINGLE_STOCK
                        ? snapshotSource : MarketDataConfig.SnapshotSource.SINA;
                quotes.snapshots(aShareInstruments,
                                new QuoteRequestOptions(mode, normalized, singleSource))
                        .forEach(quote -> result.putIfAbsent(quote.instrumentId(), quote));
            } catch (QuoteUnavailableException ignored) {
                // 某市场行情失败不能阻断其他市场最后快照的返回。
            }
        }
        for (Market market : List.of(Market.HK_STOCK, Market.US_STOCK)) {
            List<String> instruments = owned.stream()
                    .filter(item -> item.getMarket() == market)
                    .map(PortfolioItem::canonical).toList();
            if (instruments.isEmpty()) continue;
            try {
                snapshots.find(market, MarketDataConfig.SnapshotSource.SINA, instruments)
                        .forEach(quote -> result.putIfAbsent(quote.instrumentId(), quote));
            } catch (RuntimeException ignored) {
                // 保留其他市场已经取得的行情；该市场在响应中标记为无行情。
            }
        }
        return result;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    ItemView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ItemRequest request) {
        UUID currentUserId = userId(jwt);
        // 串行化同一用户的创建操作，让业务冲突稳定先于数据库唯一约束返回。
        items.lockUser(currentUserId);
        Market market = request.market() == null ? Market.A_SHARE : request.market();
        InstrumentKey instrument = parseInstrument(request.instrumentId(), market);
        SecurityCatalogItem security = catalog.requireActive(market, instrument);
        LocalDate openedOn = validatedOpenedOn(request.market(), request.openedOn(), market);
        BigDecimal costPrice = validatedCost(request.quantity(), request.costPrice());
        items.findByUserIdAndExchangeAndSymbolAndAssetType(currentUserId,
                        security.getExchange(), security.getCode(), security.getAssetType())
                .ifPresent(existing -> {
                    throw new PositionAlreadyExistsException(existing.getId());
                });
        PortfolioItem item = items.save(new PortfolioItem(currentUserId, security, openedOn,
                request.quantity(), costPrice, request.sortOrder()));
        audits.record(item.getUserId(), UserOperationAudit.Action.PORTFOLIO_CREATED,
                item.getId(), item.canonical(), item.getDisplayName(),
                item.getMarket(), item.getOpenedOn(), UserOperationAudit.Result.SUCCESS);
        return view(item, null);
    }

    @PostMapping("/{id}/accumulate")
    @Transactional
    ItemView accumulate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody AccumulateRequest request) {
        PortfolioItem item = items.findLockedByIdAndUserId(id, userId(jwt))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        item.accumulate(request.quantity(), request.costPrice());
        audits.record(item.getUserId(), UserOperationAudit.Action.PORTFOLIO_UPDATED,
                item.getId(), item.canonical(), item.getDisplayName(), item.getMarket(),
                item.getOpenedOn(), UserOperationAudit.Result.SUCCESS);
        return view(item, null);
    }

    @PutMapping("/{id}")
    @Transactional
    ItemView update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody ItemRequest request) {
        PortfolioItem item = owned(id, userId(jwt));
        Market market = request.market() == null ? item.getMarket() : request.market();
        InstrumentKey requested = parseInstrument(request.instrumentId(), market);
        if (!item.canonical().equals(requested.canonical())) {
            throw new IllegalArgumentException("更新持仓时不可变更证券代码");
        }
        if (market != item.getMarket()) throw new IllegalArgumentException("更新持仓时不可变更市场");
        SecurityCatalogItem security = catalog.requireActive(market, requested);
        item.update(security.getName(), validatedOpenedOn(request.market(), request.openedOn(), market),
                request.quantity(),
                validatedCost(request.quantity(), request.costPrice()), request.sortOrder());
        audits.record(item.getUserId(), UserOperationAudit.Action.PORTFOLIO_UPDATED,
                item.getId(), item.canonical(), item.getDisplayName(),
                item.getMarket(), item.getOpenedOn(), UserOperationAudit.Result.SUCCESS);
        return view(item, null);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        PortfolioItem item = owned(id, userId(jwt));
        items.delete(item);
        audits.record(item.getUserId(), UserOperationAudit.Action.PORTFOLIO_DELETED,
                item.getId(), item.canonical(), item.getDisplayName(),
                item.getMarket(), item.getOpenedOn(), UserOperationAudit.Result.SUCCESS);
    }

    private PortfolioItem owned(UUID id, UUID userId) {
        return items.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private ItemView view(PortfolioItem item, Quote quote) {
        if (quote == null) {
            return new ItemView(item.getId(), item.canonical(), item.getDisplayName(),
                    item.getMarket(), item.getExchange(), item.getCurrency(), item.getAssetType(),
                    item.getOpenedOn(),
                    item.getQuantity(), item.getCostPrice(), item.getSortOrder(), null,
                    null, null, null);
        }
        PortfolioCalculator.Result result = PortfolioCalculator.calculate(
                item.getQuantity(), item.getCostPrice(), quote.last());
        return new ItemView(item.getId(), item.canonical(), item.getDisplayName(),
                item.getMarket(), item.getExchange(), item.getCurrency(), item.getAssetType(),
                item.getOpenedOn(),
                item.getQuantity(), item.getCostPrice(), item.getSortOrder(), quote,
                result.marketValue(), result.profit(), result.returnPercent());
    }

    private UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }

    private BigDecimal validatedCost(BigDecimal quantity, BigDecimal costPrice) {
        BigDecimal normalized = costPrice == null ? BigDecimal.ZERO : costPrice;
        if (quantity.signum() > 0 && normalized.signum() <= 0) {
            throw new IllegalArgumentException("持仓数量大于 0 时，单位成本必须大于 0");
        }
        return normalized;
    }

    private InstrumentKey parseInstrument(String value, Market market) {
        if (market == Market.A_SHARE) {
            InstrumentId legacy = InstrumentId.parse(value);
            return new InstrumentKey(Exchange.valueOf(legacy.exchange().name()), legacy.code());
        }
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        String[] parts = normalized.split(":", 2);
        if (parts.length != 2) throw new IllegalArgumentException("证券标识必须包含交易所");
        return new InstrumentKey(Exchange.valueOf(parts[0]), parts[1]);
    }

    private LocalDate validatedOpenedOn(Market requestedMarket, LocalDate value, Market market) {
        if (value == null && requestedMarket != null) {
            throw new IllegalArgumentException("建仓日期不能为空");
        }
        LocalDate normalized = value == null ? LocalDate.now(market.timezone()) : value;
        if (normalized.isAfter(LocalDate.now(market.timezone()))) {
            throw new IllegalArgumentException("建仓日期不能晚于当前日期");
        }
        return normalized;
    }

    record ItemRequest(
            @NotBlank String instrumentId,
            @NotBlank @Size(max = 80) String displayName,
            Market market,
            LocalDate openedOn,
            @NotNull @DecimalMin("0.0000") @Digits(integer = 16, fraction = 4) BigDecimal quantity,
            @DecimalMin(value = "0.0000") @Digits(integer = 14, fraction = 6) BigDecimal costPrice,
            @Min(0) int sortOrder) {}
    record AccumulateRequest(
            @NotNull @DecimalMin(value = "0.0001") @Digits(integer = 16, fraction = 4)
            BigDecimal quantity,
            @NotNull @DecimalMin(value = "0.000001") @Digits(integer = 14, fraction = 6)
            BigDecimal costPrice) {}
    record ItemView(UUID id, String instrumentId, String displayName, Market market,
                    Exchange exchange, com.tradingassistant.market.Currency currency,
                    com.tradingassistant.market.AssetType assetType, LocalDate openedOn,
                    BigDecimal quantity,
                    BigDecimal costPrice, int sortOrder, Quote quote, BigDecimal marketValue,
                    BigDecimal profit, BigDecimal returnPercent) {}
    record PortfolioSummary(List<ItemView> items, BigDecimal totalMarketValue,
                            BigDecimal totalProfit, int unavailableQuoteCount,
                            String calculationNotice) {}
}
