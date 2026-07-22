package com.tradingassistant.marketdata;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "market_data_config")
public class MarketDataConfig {
    public enum Provider { AKSHARE }
    public enum Mode { MARKET_SNAPSHOT, SINGLE_STOCK }
    public enum SnapshotSource { EASTMONEY, SINA }
    public enum SingleSource { EASTMONEY, XUEQIU }

    @Id
    private Integer id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Provider provider;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Mode mode;
    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_source", nullable = false, length = 30)
    private SnapshotSource snapshotSource;
    @Enumerated(EnumType.STRING)
    @Column(name = "single_source", nullable = false, length = 30)
    private SingleSource singleSource;
    @Column(name = "refresh_seconds", nullable = false)
    private int refreshSeconds;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MarketDataConfig() {}

    public static MarketDataConfig defaults() {
        MarketDataConfig config = new MarketDataConfig();
        config.id = 1;
        config.provider = Provider.AKSHARE;
        config.mode = Mode.MARKET_SNAPSHOT;
        config.snapshotSource = SnapshotSource.EASTMONEY;
        config.singleSource = SingleSource.EASTMONEY;
        config.refreshSeconds = 10;
        config.updatedAt = Instant.now();
        return config;
    }

    public void update(Provider provider, Mode mode, SnapshotSource snapshotSource,
            SingleSource singleSource, int refreshSeconds) {
        this.provider = provider;
        this.mode = mode;
        this.snapshotSource = snapshotSource;
        this.singleSource = singleSource;
        this.refreshSeconds = refreshSeconds;
        this.updatedAt = Instant.now();
    }

    public Integer getId() { return id; }
    public Provider getProvider() { return provider; }
    public Mode getMode() { return mode; }
    public SnapshotSource getSnapshotSource() { return snapshotSource; }
    public SingleSource getSingleSource() { return singleSource; }
    public int getRefreshSeconds() { return refreshSeconds; }
    public Instant getUpdatedAt() { return updatedAt; }
}
