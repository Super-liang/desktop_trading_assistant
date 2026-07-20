package com.tradingassistant.portfolio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository extends JpaRepository<PortfolioItem, UUID> {
    List<PortfolioItem> findAllByUserIdOrderBySortOrderAscCreatedAtAsc(UUID userId);
    Optional<PortfolioItem> findByIdAndUserId(UUID id, UUID userId);
}

