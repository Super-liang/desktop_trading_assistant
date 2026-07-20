package com.tradingassistant.auth;

import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

    public AccountController(UserRepository users, RefreshTokenRepository refreshTokens,
            TokenService tokens, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logoutAll(@AuthenticationPrincipal Jwt jwt) {
        tokens.revokeAll(UUID.fromString(jwt.getSubject()));
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
}
