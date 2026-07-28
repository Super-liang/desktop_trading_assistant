package com.tradingassistant.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_operation_audits")
public class UserOperationAudit {
    public enum Action { PORTFOLIO_CREATED, PORTFOLIO_UPDATED, PORTFOLIO_DELETED, PASSWORD_CHANGED }
    public enum Result { SUCCESS, FAILURE }

    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "actor_user_id", nullable = false) private UUID actorUserId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private Action action;
    @Column(name = "portfolio_item_id") private UUID portfolioItemId;
    @Column(name = "instrument_id", length = 24) private String instrumentId;
    @Column(name = "instrument_name", length = 80) private String instrumentName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Result result;
    @Column(name = "request_id", nullable = false) private UUID requestId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected UserOperationAudit() {}
    public UserOperationAudit(UUID userId, Action action, UUID portfolioItemId,
            String instrumentId, String instrumentName, Result result) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.actorUserId = userId;
        this.action = action;
        this.portfolioItemId = portfolioItemId;
        this.instrumentId = instrumentId;
        this.instrumentName = instrumentName;
        this.result = result;
        this.requestId = UUID.randomUUID();
        this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getActorUserId() { return actorUserId; }
    public Action getAction() { return action; }
    public UUID getPortfolioItemId() { return portfolioItemId; }
    public String getInstrumentId() { return instrumentId; }
    public String getInstrumentName() { return instrumentName; }
    public Result getResult() { return result; }
    public UUID getRequestId() { return requestId; }
    public Instant getCreatedAt() { return createdAt; }
}
