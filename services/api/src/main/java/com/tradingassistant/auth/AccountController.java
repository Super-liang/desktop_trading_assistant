package com.tradingassistant.auth;

import com.tradingassistant.audit.UserOperationAudit;
import com.tradingassistant.audit.UserOperationAuditService;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/me")
public class AccountController {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final TokenService tokens;
    private final PasswordEncoder passwordEncoder;
    private final UserOperationAuditService audits;

    public AccountController(UserRepository users, RefreshTokenRepository refreshTokens,
            TokenService tokens, PasswordEncoder passwordEncoder,
            UserOperationAuditService audits) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.audits = audits;
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logoutAll(@AuthenticationPrincipal Jwt jwt) {
        tokens.revokeAll(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void changePassword(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        User user = users.findById(userId).orElseThrow(() -> new AuthException("账号不存在"));
        try {
            validatePasswordChange(user, request);
        } catch (RuntimeException exception) {
            audits.recordFailure(userId, UserOperationAudit.Action.PASSWORD_CHANGED);
            throw exception;
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        tokens.revokeAll(userId);
        audits.record(userId, UserOperationAudit.Action.PASSWORD_CHANGED,
                null, null, null, UserOperationAudit.Result.SUCCESS);
    }

    private void validatePasswordChange(User user, ChangePasswordRequest request) {
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AuthException("当前密码错误");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("两次输入的新密码不一致");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("新密码不能与当前密码相同");
        }
        if (request.newPassword().length() < 10 || request.newPassword().length() > 72
                || !request.newPassword().matches(".*[A-Za-z].*")
                || !request.newPassword().matches(".*\\d.*")) {
            throw new IllegalArgumentException("新密码需为 10 到 72 位且同时包含字母和数字");
        }
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void delete(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeleteAccountRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        User user = users.findById(userId).orElseThrow(() -> new AuthException("账号不存在"));
        if (user.getRole() == User.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "管理员账号需先完成审计责任转移，不能自助注销");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthException("密码校验失败");
        }
        refreshTokens.deleteAllByUserId(userId);
        users.deleteById(userId);
    }

    record DeleteAccountRequest(@NotBlank String password) {}
    record ChangePasswordRequest(@NotBlank String currentPassword,
            @NotBlank @Size(max = 72) String newPassword,
            @NotBlank String confirmPassword) {}
}
