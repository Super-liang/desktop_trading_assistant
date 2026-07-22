package com.tradingassistant.catalog;

import com.tradingassistant.quote.InstrumentId;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "securities", uniqueConstraints = @UniqueConstraint(
        name = "uq_securities_exchange_code", columnNames = {"exchange", "code"}))
public class SecurityCatalogItem {
    public enum Status { ACTIVE, INACTIVE }

    @Id @Column(name = "instrument_id", length = 16) private String instrumentId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private InstrumentId.Exchange exchange;
    @Column(nullable = false, length = 12) private String code;
    @Column(nullable = false, length = 80) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "asset_type", nullable = false, length = 20)
    private InstrumentId.AssetType assetType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false, length = 30) private String source;
    @Column(name = "source_updated_at", nullable = false) private Instant sourceUpdatedAt;
    @Column(name = "last_seen_at", nullable = false) private Instant lastSeenAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected SecurityCatalogItem() {}

    public SecurityCatalogItem(InstrumentId instrument, String name, String source, Instant seenAt) {
        this.instrumentId = instrument.canonical();
        this.exchange = instrument.exchange();
        this.code = instrument.code();
        this.name = requireName(name);
        this.assetType = instrument.assetType();
        this.status = Status.ACTIVE;
        this.source = source;
        this.sourceUpdatedAt = seenAt;
        this.lastSeenAt = seenAt;
        this.createdAt = seenAt;
        this.updatedAt = seenAt;
    }

    public void refresh(String latestName, String latestSource, Instant seenAt) {
        this.name = requireName(latestName);
        this.status = Status.ACTIVE;
        this.source = latestSource;
        this.sourceUpdatedAt = seenAt;
        this.lastSeenAt = seenAt;
        this.updatedAt = seenAt;
    }

    private static String requireName(String value) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty() || normalized.length() > 80) {
            throw new IllegalArgumentException("证券名称无效");
        }
        return normalized;
    }

    public String getInstrumentId() { return instrumentId; }
    public InstrumentId.Exchange getExchange() { return exchange; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public InstrumentId.AssetType getAssetType() { return assetType; }
    public Status getStatus() { return status; }
    public Instant getSourceUpdatedAt() { return sourceUpdatedAt; }
}
