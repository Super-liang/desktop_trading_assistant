package com.tradingassistant.catalog;

import com.tradingassistant.market.Market;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "market_sync_runs", uniqueConstraints = @UniqueConstraint(
        name = "uq_market_sync_run", columnNames = {"market", "trading_date", "job_type"}))
public class MarketSyncRun {
    public enum Status { RUNNING, SUCCESS, FAILED }

    @Id private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Market market;
    @Column(name = "trading_date", nullable = false) private LocalDate tradingDate;
    @Column(name = "job_type", nullable = false, length = 30) private String jobType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "error_type", length = 80) private String errorType;

    protected MarketSyncRun() {}

    public MarketSyncRun(Market market, LocalDate tradingDate, String jobType, Instant startedAt) {
        this.id = UUID.randomUUID();
        this.market = market;
        this.tradingDate = tradingDate;
        this.jobType = jobType;
        retry(startedAt);
    }

    public void retry(Instant at) {
        status = Status.RUNNING;
        startedAt = at;
        completedAt = null;
        errorType = null;
    }

    public void succeed(Instant at) {
        status = Status.SUCCESS;
        completedAt = at;
    }

    public void fail(Instant at, RuntimeException exception) {
        status = Status.FAILED;
        completedAt = at;
        errorType = exception.getClass().getSimpleName();
    }

    public Status getStatus() { return status; }
}
