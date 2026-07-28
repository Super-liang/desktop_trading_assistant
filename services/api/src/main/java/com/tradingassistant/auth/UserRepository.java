package com.tradingassistant.auth;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    long countByRoleAndStatus(User.Role role, User.Status status);
    List<User> findAllByStatus(User.Status status);
    Page<User> findByEmailContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
            String email, String displayName, Pageable pageable);
}
