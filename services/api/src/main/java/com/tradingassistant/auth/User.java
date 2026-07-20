package com.tradingassistant.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
    public enum Role { USER, ADMIN }
    public enum Status { ACTIVE, DISABLED }

    @Id
    private UUID id;
    @Column(nullable = false, unique = true, length = 320)
    private String email;
    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected User() {}

    public User(String email, String displayName, String passwordHash, Role role) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = Status.ACTIVE;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void recordLogin() { this.lastLoginAt = Instant.now(); }
    public void setStatus(Status status) { this.status = status; }
}

