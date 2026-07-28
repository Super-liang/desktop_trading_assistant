package com.tradingassistant.admin;

import com.tradingassistant.audit.UserOperationAudit;
import com.tradingassistant.audit.UserOperationAuditRepository;
import com.tradingassistant.auth.RefreshTokenRepository;
import com.tradingassistant.auth.User;
import com.tradingassistant.auth.UserRepository;
import com.tradingassistant.performance.PerformanceService;
import com.tradingassistant.performance.PerformanceSummary;
import com.tradingassistant.portfolio.PortfolioItem;
import com.tradingassistant.portfolio.PortfolioRepository;
import com.tradingassistant.quote.InstrumentId;
import com.tradingassistant.quote.Quote;
import com.tradingassistant.quote.QuoteProviderRegistry;
import com.tradingassistant.quote.QuoteUnavailableException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.format.annotation.DateTimeFormat;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AdminAuditRepository audits;
    private final UserOperationAuditRepository userAudits;
    private final PortfolioRepository portfolios;
    private final QuoteProviderRegistry quotes;
    private final PerformanceService performance;

    public AdminController(UserRepository users, RefreshTokenRepository refreshTokens,
            AdminAuditRepository audits, UserOperationAuditRepository userAudits,
            PortfolioRepository portfolios, QuoteProviderRegistry quotes,
            PerformanceService performance) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.audits = audits;
        this.userAudits = userAudits;
        this.portfolios = portfolios;
        this.quotes = quotes;
        this.performance = performance;
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

    @GetMapping("/users/{id}/overview")
    UserOverview overview(@PathVariable UUID id) {
        User user = requireUser(id);
        return new UserOverview(UserSummary.from(user), portfolios.countByUserId(id),
                performance.current(id));
    }

    @GetMapping("/users/{id}/holdings")
    HoldingsResponse holdings(@PathVariable UUID id) {
        requireUser(id);
        List<PortfolioItem> owned = portfolios.findAllByUserIdOrderBySortOrderAscCreatedAtAsc(id);
        Set<String> available = availableQuotes(owned);
        return new HoldingsResponse(owned.stream().map(item -> new HoldingSummary(item.canonical(),
                item.getDisplayName(), item.getExchange().name(), available.contains(item.canonical())))
                .toList());
    }

    @GetMapping("/users/{id}/audits")
    Page<UserAuditSummary> userAudits(@PathVariable UUID id,
            @RequestParam(required = false) UserOperationAudit.Action action,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireUser(id);
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("审计开始时间不能晚于结束时间");
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 100));
        return userAudits.search(id, action, from, to, pageable).map(UserAuditSummary::from);
    }

    private User requireUser(UUID id) {
        return users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private Set<String> availableQuotes(List<PortfolioItem> owned) {
        if (owned.isEmpty()) return Set.of();
        try {
            return quotes.snapshots(owned.stream().map(item ->
                            InstrumentId.parse(item.canonical())).toList()).stream()
                    .map(Quote::instrumentId).collect(Collectors.toSet());
        } catch (QuoteUnavailableException exception) {
            return Set.of();
        }
    }

    record StatusRequest(User.Status status) {}
    record UserSummary(UUID id, String email, String displayName, User.Role role,
                       User.Status status, Instant createdAt, Instant lastLoginAt,
                       String lastLoginStatus) {
        static UserSummary from(User user) {
            return new UserSummary(user.getId(), user.getEmail(), user.getDisplayName(),
                    user.getRole(), user.getStatus(), user.getCreatedAt(), user.getLastLoginAt(),
                    user.getLastLoginAt() == null ? "NEVER" : "RECORDED");
        }
    }
    record AuditSummary(UUID id, UUID adminUserId, String action, UUID targetUserId,
                        String result, Instant createdAt) {
        static AuditSummary from(AdminAudit audit) {
            return new AuditSummary(audit.getId(), audit.getAdminUserId(), audit.getAction(),
                    audit.getTargetUserId(), audit.getResult(), audit.getCreatedAt());
        }
    }
    record UserOverview(UserSummary user, long holdingCount, PerformanceSummary performance) {}
    record HoldingSummary(String instrumentId, String displayName, String exchange,
                          boolean quoteAvailable) {}
    record HoldingsResponse(List<HoldingSummary> content) {}
    record UserAuditSummary(UUID id, UserOperationAudit.Action action, String instrumentId,
                            String instrumentName, UserOperationAudit.Result result,
                            Instant createdAt) {
        static UserAuditSummary from(UserOperationAudit audit) {
            return new UserAuditSummary(audit.getId(), audit.getAction(), audit.getInstrumentId(),
                    audit.getInstrumentName(), audit.getResult(), audit.getCreatedAt());
        }
    }
}
