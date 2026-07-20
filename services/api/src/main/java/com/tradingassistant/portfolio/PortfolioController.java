package com.tradingassistant.portfolio;

import com.tradingassistant.quote.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
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

    public PortfolioController(PortfolioRepository items, QuoteProviderRegistry quotes) {
        this.items = items;
        this.quotes = quotes;
    }

    @GetMapping
    PortfolioSummary list(@AuthenticationPrincipal Jwt jwt) {
        List<PortfolioItem> owned = items.findAllByUserIdOrderBySortOrderAscCreatedAtAsc(userId(jwt));
        Map<String, Quote> latest = quotes
                .snapshots(owned.stream().map(item -> InstrumentId.parse(item.canonical())).toList())
                .stream().collect(java.util.stream.Collectors.toMap(Quote::instrumentId, value -> value));
        List<ItemView> views = owned.stream().map(item -> view(item, latest.get(item.canonical()))).toList();
        BigDecimal marketValue = views.stream().map(ItemView::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal profit = views.stream().map(ItemView::profit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PortfolioSummary(views, marketValue, profit, "不含手续费、税费、分红送转影响");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    ItemView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ItemRequest request) {
        InstrumentId instrument = InstrumentId.parse(request.instrumentId());
        PortfolioItem item = items.save(new PortfolioItem(userId(jwt), instrument,
                request.displayName(), request.quantity(), request.costPrice(), request.sortOrder()));
        Quote quote = quotes.snapshots(List.of(instrument)).get(0);
        return view(item, quote);
    }

    @PutMapping("/{id}")
    @Transactional
    ItemView update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody ItemRequest request) {
        PortfolioItem item = owned(id, userId(jwt));
        InstrumentId requested = InstrumentId.parse(request.instrumentId());
        if (!item.canonical().equals(requested.canonical())) {
            throw new IllegalArgumentException("更新持仓时不可变更证券代码");
        }
        item.update(request.displayName(), request.quantity(), request.costPrice(), request.sortOrder());
        return view(item, quotes.snapshots(List.of(requested)).get(0));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        items.delete(owned(id, userId(jwt)));
    }

    private PortfolioItem owned(UUID id, UUID userId) {
        return items.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private ItemView view(PortfolioItem item, Quote quote) {
        PortfolioCalculator.Result result = PortfolioCalculator.calculate(
                item.getQuantity(), item.getCostPrice(), quote.last());
        return new ItemView(item.getId(), item.canonical(), item.getDisplayName(),
                item.getQuantity(), item.getCostPrice(), item.getSortOrder(), quote,
                result.marketValue(), result.profit(), result.returnPercent());
    }

    private UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }

    record ItemRequest(
            @NotBlank String instrumentId,
            @NotBlank @Size(max = 80) String displayName,
            @NotNull @DecimalMin("0.0000") BigDecimal quantity,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal costPrice,
            @Min(0) int sortOrder) {}
    record ItemView(UUID id, String instrumentId, String displayName, BigDecimal quantity,
                    BigDecimal costPrice, int sortOrder, Quote quote, BigDecimal marketValue,
                    BigDecimal profit, BigDecimal returnPercent) {}
    record PortfolioSummary(List<ItemView> items, BigDecimal totalMarketValue,
                            BigDecimal totalProfit, String calculationNotice) {}
}
