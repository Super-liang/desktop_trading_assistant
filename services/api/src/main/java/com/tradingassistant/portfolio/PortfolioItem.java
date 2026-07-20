package com.tradingassistant.portfolio;

import com.tradingassistant.quote.InstrumentId;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "portfolio_items",
        uniqueConstraints = @UniqueConstraint(name = "uq_portfolio_user_instrument",
                columnNames = {"user_id", "exchange", "symbol", "asset_type"}))
public class PortfolioItem {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private InstrumentId.Exchange exchange;
    @Column(nullable = false, length = 12) private String symbol;
    @Enumerated(EnumType.STRING) @Column(name = "asset_type", nullable = false, length = 20)
    private InstrumentId.AssetType assetType;
    @Column(name = "display_name", nullable = false, length = 80) private String displayName;
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
        this.exchange = instrument.exchange();
        this.symbol = instrument.code();
        this.assetType = instrument.assetType();
        this.displayName = displayName;
        this.quantity = quantity;
        this.costPrice = costPrice;
        this.sortOrder = sortOrder;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }
    public void update(String displayName, BigDecimal quantity, BigDecimal costPrice, int sortOrder) {
        this.displayName = displayName;
        this.quantity = quantity;
        this.costPrice = costPrice;
        this.sortOrder = sortOrder;
        this.updatedAt = Instant.now();
    }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public InstrumentId.Exchange getExchange() { return exchange; }
    public String getSymbol() { return symbol; }
    public InstrumentId.AssetType getAssetType() { return assetType; }
    public String getDisplayName() { return displayName; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getCostPrice() { return costPrice; }
    public int getSortOrder() { return sortOrder; }
    public String canonical() { return exchange + ":" + symbol; }
}

