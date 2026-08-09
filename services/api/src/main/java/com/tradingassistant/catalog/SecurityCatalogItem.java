package com.tradingassistant.catalog;

import com.tradingassistant.market.AssetType;
import com.tradingassistant.market.Currency;
import com.tradingassistant.market.Exchange;
import com.tradingassistant.market.InstrumentKey;
import com.tradingassistant.market.Market;
import com.tradingassistant.quote.InstrumentId;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "securities", uniqueConstraints = @UniqueConstraint(
        name = "uq_securities_exchange_code", columnNames = {"exchange", "code"}))
public class SecurityCatalogItem {
    public enum Status { ACTIVE, INACTIVE }

    @Id @Column(name = "instrument_id", length = 32) private String instrumentId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private Exchange exchange;
    @Column(nullable = false, length = 12) private String code;
    @Column(nullable = false, length = 80) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Market market;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 3) private Currency currency;
    @Column(name = "provider_symbol", nullable = false, length = 32) private String providerSymbol;
    @Enumerated(EnumType.STRING) @Column(name = "asset_type", nullable = false, length = 20)
    private AssetType assetType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false, length = 30) private String source;
    @Column(name = "source_updated_at", nullable = false) private Instant sourceUpdatedAt;
    @Column(name = "last_seen_at", nullable = false) private Instant lastSeenAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected SecurityCatalogItem() {}

    public SecurityCatalogItem(InstrumentId instrument, String name, String source, Instant seenAt) {
        this(instrument.canonical(), instrument.code(), name, Market.A_SHARE,
                Exchange.valueOf(instrument.exchange().name()), Currency.CNY,
                AssetType.valueOf(instrument.assetType().name()), instrument.code(), source, seenAt);
    }

    public SecurityCatalogItem(String instrumentId, String code, String name, Market market,
            Exchange exchange, Currency currency, AssetType assetType, String providerSymbol,
            String source, Instant seenAt) {
        InstrumentKey key = new InstrumentKey(exchange, code);
        if (!key.canonical().equals(instrumentId) || !supports(market, exchange)
                || currency != market.currency()) {
            throw new IllegalArgumentException("证券目录市场字段不一致");
        }
        if (assetType == AssetType.OPEN_END_FUND && market != Market.PUBLIC_FUND
                || market == Market.PUBLIC_FUND && assetType != AssetType.OPEN_END_FUND) {
            throw new IllegalArgumentException("证券资产类型与市场不一致");
        }
        this.instrumentId = key.canonical();
        this.exchange = exchange;
        this.code = key.code();
        this.name = requireName(name);
        this.market = market;
        this.currency = currency;
        this.providerSymbol = requireProviderSymbol(providerSymbol);
        this.assetType = assetType;
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

    public void deactivate(Instant changedAt) {
        this.status = Status.INACTIVE;
        this.updatedAt = changedAt;
    }

    private static boolean supports(Market market, Exchange exchange) {
        return switch (market) {
            case A_SHARE -> exchange == Exchange.SSE || exchange == Exchange.SZSE
                    || exchange == Exchange.BSE;
            case HK_STOCK -> exchange == Exchange.HKEX;
            case US_STOCK -> exchange == Exchange.NASDAQ || exchange == Exchange.NYSE
                    || exchange == Exchange.AMEX;
            case PUBLIC_FUND -> exchange == Exchange.CN_FUND;
        };
    }

    private static String requireProviderSymbol(String value) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty() || normalized.length() > 32) {
            throw new IllegalArgumentException("上游证券标识无效");
        }
        return normalized;
    }

    private static String requireName(String value) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty() || normalized.length() > 80) {
            throw new IllegalArgumentException("证券名称无效");
        }
        return normalized;
    }

    public String getInstrumentId() { return instrumentId; }
    public Exchange getExchange() { return exchange; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public AssetType getAssetType() { return assetType; }
    public Market getMarket() { return market; }
    public Currency getCurrency() { return currency; }
    public String getProviderSymbol() { return providerSymbol; }
    public Status getStatus() { return status; }
    public Instant getSourceUpdatedAt() { return sourceUpdatedAt; }
}
