package com.tradingassistant.auth;

import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    @Transactional
    public TokenService.AuthResponse register(String email, String displayName, String password) {
        String normalized = normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw new AuthException("该邮箱已注册");
        }
        User user = users.save(new User(normalized, displayName.strip(),
                passwordEncoder.encode(password), User.Role.USER));
        return tokens.issue(user);
    }

    @Transactional
    public TokenService.AuthResponse login(String email, String password) {
        User user = users.findByEmail(normalizeEmail(email))
                .filter(value -> value.getStatus() == User.Status.ACTIVE)
                .filter(value -> passwordEncoder.matches(password, value.getPasswordHash()))
                .orElseThrow(() -> new AuthException("邮箱或密码错误"));
        user.recordLogin();
        return tokens.issue(user);
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}

