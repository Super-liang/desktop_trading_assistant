package com.tradingassistant.admin;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_audits")
public class AdminAudit {
    @Id
    private UUID id;
    @Column(name = "admin_user_id", nullable = false)
    private UUID adminUserId;
    @Column(nullable = false, length = 60)
    private String action;
    @Column(name = "target_user_id")
    private UUID targetUserId;
    @Column(nullable = false, length = 20)
    private String result;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AdminAudit() {}
    public AdminAudit(UUID adminUserId, String action, UUID targetUserId, String result) {
        this.id = UUID.randomUUID();
        this.adminUserId = adminUserId;
        this.action = action;
        this.targetUserId = targetUserId;
        this.result = result;
        this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public UUID getAdminUserId() { return adminUserId; }
    public String getAction() { return action; }
    public UUID getTargetUserId() { return targetUserId; }
    public String getResult() { return result; }
    public Instant getCreatedAt() { return createdAt; }
}

