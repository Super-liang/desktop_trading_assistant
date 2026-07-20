package com.tradingassistant.admin;

import com.tradingassistant.auth.RefreshTokenRepository;
import com.tradingassistant.auth.User;
import com.tradingassistant.auth.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AdminAuditRepository audits;

    public AdminController(UserRepository users, RefreshTokenRepository refreshTokens,
            AdminAuditRepository audits) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.audits = audits;
    }

    @GetMapping("/users")
    Page<UserSummary> users(@RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return users.findByEmailContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
                query, query, pageable).map(UserSummary::from);
    }

    @PatchMapping("/users/{id}/status")
    @Transactional
    UserSummary status(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @RequestBody StatusRequest request) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        if (actorId.equals(id) && request.status() == User.Status.DISABLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "不能禁用当前管理员账号");
        }
        User target = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (target.getRole() == User.Role.ADMIN && request.status() == User.Status.DISABLED
                && users.countByRoleAndStatus(User.Role.ADMIN, User.Status.ACTIVE) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "必须保留至少一个启用的管理员");
        }
        target.setStatus(request.status());
        if (request.status() == User.Status.DISABLED) {
            refreshTokens.findAllByUserId(id).forEach(token -> token.revoke());
        }
        audits.save(new AdminAudit(actorId,
                "USER_STATUS_" + request.status(), id, "SUCCESS"));
        return UserSummary.from(target);
    }

    @GetMapping("/audits")
    Page<AuditSummary> audits(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return audits.findAllByOrderByCreatedAtDesc(
                PageRequest.of(page, Math.min(Math.max(size, 1), 100)))
                .map(AuditSummary::from);
    }

    record StatusRequest(User.Status status) {}
    record UserSummary(UUID id, String email, String displayName, User.Role role,
                       User.Status status, Instant createdAt, Instant lastLoginAt) {
        static UserSummary from(User user) {
            return new UserSummary(user.getId(), user.getEmail(), user.getDisplayName(),
                    user.getRole(), user.getStatus(), user.getCreatedAt(), user.getLastLoginAt());
        }
    }
    record AuditSummary(UUID id, UUID adminUserId, String action, UUID targetUserId,
                        String result, Instant createdAt) {
        static AuditSummary from(AdminAudit audit) {
            return new AuditSummary(audit.getId(), audit.getAdminUserId(), audit.getAction(),
                    audit.getTargetUserId(), audit.getResult(), audit.getCreatedAt());
        }
    }
}
