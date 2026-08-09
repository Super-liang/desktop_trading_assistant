package com.tradingassistant.portfolio;

import com.tradingassistant.quote.InstrumentId;
import com.tradingassistant.catalog.SecurityCatalogItem;
import com.tradingassistant.market.AssetType;
import com.tradingassistant.market.Currency;
import com.tradingassistant.market.Exchange;
import com.tradingassistant.market.Market;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(name = "portfolio_items",
        uniqueConstraints = @UniqueConstraint(name = "uq_portfolio_user_instrument",
                columnNames = {"user_id", "exchange", "symbol", "asset_type"}))
public class PortfolioItem {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private Exchange exchange;
    @Column(nullable = false, length = 12) private String symbol;
    @Enumerated(EnumType.STRING) @Column(name = "asset_type", nullable = false, length = 20)
    private AssetType assetType;
    @Column(name = "display_name", nullable = false, length = 80) private String displayName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Market market;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 3) private Currency currency;
    @Column(name = "opened_on", nullable = false) private LocalDate openedOn;
    @Column(nullable = false, precision = 20, scale = 4) private BigDecimal quantity;
    @Column(name = "cost_price", nullable = false, precision = 20, scale = 4) private BigDecimal costPrice;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected PortfolioItem() {}
    public PortfolioItem(UUID userId, InstrumentId instrument, String displayName,
            BigDecimal quantity, BigDecimal costPrice, int sortOrder) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.exchange = Exchange.valueOf(instrument.exchange().name());
        this.symbol = instrument.code();
        this.assetType = AssetType.valueOf(instrument.assetType().name());
        this.displayName = displayName;
        this.market = Market.A_SHARE;
        this.currency = Currency.CNY;
        this.openedOn = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        this.quantity = quantity;
        this.costPrice = costPrice;
        this.sortOrder = sortOrder;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public PortfolioItem(UUID userId, SecurityCatalogItem security, LocalDate openedOn,
            BigDecimal quantity, BigDecimal costPrice, int sortOrder) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.exchange = security.getExchange();
        this.symbol = security.getCode();
        this.assetType = security.getAssetType();
        this.displayName = security.getName();
        this.market = security.getMarket();
        this.currency = security.getCurrency();
        this.openedOn = openedOn;
        this.quantity = quantity;
        this.costPrice = costPrice;
        this.sortOrder = sortOrder;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void update(String displayName, LocalDate openedOn, BigDecimal quantity,
            BigDecimal costPrice, int sortOrder) {
        this.displayName = displayName;
        this.openedOn = openedOn;
        this.quantity = quantity;
        this.costPrice = costPrice;
        this.sortOrder = sortOrder;
        this.updatedAt = Instant.now();
    }
    public void update(String displayName, BigDecimal quantity, BigDecimal costPrice, int sortOrder) {
        update(displayName, openedOn, quantity, costPrice, sortOrder);
    }
    public void accumulate(BigDecimal addedQuantity, BigDecimal addedCostPrice) {
        if (addedQuantity == null || addedQuantity.signum() <= 0
                || addedCostPrice == null || addedCostPrice.signum() <= 0) {
            throw new IllegalArgumentException("追加数量和单位成本必须大于 0");
        }
        BigDecimal nextQuantity = quantity.add(addedQuantity);
        BigDecimal nextCost = quantity.signum() == 0
                ? addedCostPrice
                : quantity.multiply(costPrice).add(addedQuantity.multiply(addedCostPrice))
                        .divide(nextQuantity, 4, RoundingMode.HALF_UP);
        this.quantity = nextQuantity;
        this.costPrice = nextCost.setScale(4, RoundingMode.HALF_UP);
        this.updatedAt = Instant.now();
    }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Exchange getExchange() { return exchange; }
    public String getSymbol() { return symbol; }
    public AssetType getAssetType() { return assetType; }
    public String getDisplayName() { return displayName; }
    public Market getMarket() { return market; }
    public Currency getCurrency() { return currency; }
    public LocalDate getOpenedOn() { return openedOn; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getCostPrice() { return costPrice; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String canonical() { return exchange + ":" + symbol; }
}
