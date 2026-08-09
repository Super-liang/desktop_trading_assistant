package com.tradingassistant.catalog;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "fund_nav_quotes")
@IdClass(FundNavQuoteId.class)
public class FundNavQuote {
    @Id @Column(name = "instrument_id", length = 32) private String instrumentId;
    @Id @Column(name = "nav_date") private LocalDate navDate;
    @Column(name = "unit_nav", nullable = false, precision = 20, scale = 6)
    private BigDecimal unitNav;
    @Column(nullable = false, length = 30) private String source;
    @Column(name = "source_updated_at", nullable = false) private Instant sourceUpdatedAt;

    protected FundNavQuote() {}

    public FundNavQuote(String instrumentId, LocalDate navDate, BigDecimal unitNav,
            String source, Instant sourceUpdatedAt) {
        if (unitNav == null || unitNav.signum() <= 0) {
            throw new IllegalArgumentException("基金单位净值必须大于零");
        }
        this.instrumentId = instrumentId;
        this.navDate = navDate;
        this.unitNav = unitNav;
        this.source = source;
        this.sourceUpdatedAt = sourceUpdatedAt;
    }

    public String getInstrumentId() { return instrumentId; }
    public LocalDate getNavDate() { return navDate; }
    public BigDecimal getUnitNav() { return unitNav; }
}
