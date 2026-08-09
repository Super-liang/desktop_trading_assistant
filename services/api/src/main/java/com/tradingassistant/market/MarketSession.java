package com.tradingassistant.market;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "market_sessions")
@IdClass(MarketSessionId.class)
public class MarketSession {
    @Id @Enumerated(EnumType.STRING) @Column(length = 20) private Market market;
    @Id @Column(name = "trading_date") private LocalDate tradingDate;
    @Column(nullable = false, length = 64) private String timezone;
    @Column(name = "open_at", nullable = false) private Instant openAt;
    @Column(name = "break_start_at") private Instant breakStartAt;
    @Column(name = "break_end_at") private Instant breakEndAt;
    @Column(name = "close_at", nullable = false) private Instant closeAt;
    @Column(name = "early_close", nullable = false) private boolean earlyClose;
    @Column(nullable = false, length = 60) private String source;
    @Column(name = "manual_override", nullable = false) private boolean manualOverride;
    @Column(name = "synced_at", nullable = false) private Instant syncedAt;

    protected MarketSession() {}

    public MarketSession(Market market, LocalDate tradingDate, String timezone, Instant openAt,
            Instant breakStartAt, Instant breakEndAt, Instant closeAt, boolean earlyClose,
            String source, boolean manualOverride, Instant syncedAt) {
        this.market = market;
        this.tradingDate = tradingDate;
        this.timezone = timezone;
        this.openAt = openAt;
        this.breakStartAt = breakStartAt;
        this.breakEndAt = breakEndAt;
        this.closeAt = closeAt;
        this.earlyClose = earlyClose;
        this.source = source;
        this.manualOverride = manualOverride;
        this.syncedAt = syncedAt;
    }

    public void refreshFrom(MarketSession incoming) {
        if (manualOverride) return;
        timezone = incoming.timezone;
        openAt = incoming.openAt;
        breakStartAt = incoming.breakStartAt;
        breakEndAt = incoming.breakEndAt;
        closeAt = incoming.closeAt;
        earlyClose = incoming.earlyClose;
        source = incoming.source;
        syncedAt = incoming.syncedAt;
    }

    public Market getMarket() { return market; }
    public LocalDate getTradingDate() { return tradingDate; }
    public String getTimezone() { return timezone; }
    public Instant getOpenAt() { return openAt; }
    public Instant getBreakStartAt() { return breakStartAt; }
    public Instant getBreakEndAt() { return breakEndAt; }
    public Instant getCloseAt() { return closeAt; }
    public boolean isEarlyClose() { return earlyClose; }
    public String getSource() { return source; }
    public boolean isManualOverride() { return manualOverride; }
    public Instant getSyncedAt() { return syncedAt; }
}
