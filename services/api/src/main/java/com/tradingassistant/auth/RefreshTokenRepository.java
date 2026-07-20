package com.tradingassistant.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    List<RefreshToken> findAllByFamilyId(UUID familyId);
    List<RefreshToken> findAllByUserId(UUID userId);
    void deleteAllByUserId(UUID userId);
}

