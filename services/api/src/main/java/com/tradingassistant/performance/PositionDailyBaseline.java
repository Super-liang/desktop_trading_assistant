package com.tradingassistant.performance;

import com.tradingassistant.market.Currency;
import com.tradingassistant.market.Market;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "position_daily_baselines")
@IdClass(PositionDailyBaselineId.class)
public class PositionDailyBaseline {
    public enum Status { COMPLETE, UNAVAILABLE }

    @Id @Column(name = "position_id") private UUID positionId;
    @Id @Column(name = "trading_date") private LocalDate tradingDate;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Market market;
    @Column(name = "opening_quantity", precision = 20, scale = 4) private BigDecimal openingQuantity;
    @Column(name = "opening_price", precision = 20, scale = 6) private BigDecimal openingPrice;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 3) private Currency currency;
    @Column(name = "quote_source", length = 40) private String quoteSource;
    @Column(name = "captured_at") private Instant capturedAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Enumerated(EnumType.STRING) @Column(name = "status_reason", length = 30)
    private Reason statusReason;

    public enum Reason { MISSING_OPEN, MUTATED_AFTER_OPEN }

    protected PositionDailyBaseline() {}

    public PositionDailyBaseline(UUID positionId, UUID userId, Market market,
            LocalDate tradingDate, Currency currency) {
        this.positionId = positionId;
        this.userId = userId;
        this.market = market;
        this.tradingDate = tradingDate;
        this.currency = currency;
    }

    public void complete(BigDecimal quantity, BigDecimal price, String source, Instant at) {
        openingQuantity = quantity;
        openingPrice = price;
        quoteSource = source;
        capturedAt = at;
        status = Status.COMPLETE;
        statusReason = null;
    }

    public void unavailable(Reason reason, String source, Instant at) {
        openingQuantity = null;
        openingPrice = null;
        quoteSource = source;
        capturedAt = at;
        status = Status.UNAVAILABLE;
        statusReason = reason;
    }

    public UUID getPositionId() { return positionId; }
    public LocalDate getTradingDate() { return tradingDate; }
    public BigDecimal getOpeningQuantity() { return openingQuantity; }
    public BigDecimal getOpeningPrice() { return openingPrice; }
    public Instant getCapturedAt() { return capturedAt; }
    public Status getStatus() { return status; }
    public Reason getStatusReason() { return statusReason; }
}
