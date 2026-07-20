package com.tradingassistant.auth;

import com.tradingassistant.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;
    private final AppProperties properties;
    private final java.security.SecureRandom random = new java.security.SecureRandom();

    public TokenService(JwtEncoder encoder, RefreshTokenRepository refreshTokens,
            UserRepository users, AppProperties properties) {
        this.encoder = encoder;
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.properties = properties;
    }

    @Transactional
    public AuthResponse issue(User user) {
        return issue(user, UUID.randomUUID());
    }

    @Transactional
    public AuthResponse refresh(String rawToken) {
        RefreshToken existing = refreshTokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new AuthException("刷新令牌无效"));
        if (!existing.isUsable()) {
            revokeFamily(existing.getFamilyId());
            throw new AuthException("刷新令牌已失效");
        }
        existing.revoke();
        User user = users.findById(existing.getUserId())
                .filter(value -> value.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new AuthException("账号不可用"));
        return issue(user, existing.getFamilyId());
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(RefreshToken::revoke);
    }

    @Transactional
    public void revokeAll(UUID userId) {
        refreshTokens.findAllByUserId(userId).forEach(RefreshToken::revoke);
    }

    private AuthResponse issue(User user, UUID familyId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.jwt().accessMinutes(), ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("trading-assistant")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("name", user.getDisplayName())
                .claim("roles", List.of(user.getRole().name()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String access = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String rawRefresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(
                user.getId(),
                familyId,
                hash(rawRefresh),
                now.plus(properties.jwt().refreshDays(), ChronoUnit.DAYS)));
        return new AuthResponse(access, rawRefresh, expiresAt, user.getRole().name());
    }

    private void revokeFamily(UUID familyId) {
        refreshTokens.findAllByFamilyId(familyId).forEach(RefreshToken::revoke);
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record AuthResponse(String accessToken, String refreshToken, Instant expiresAt, String role) {}
}

