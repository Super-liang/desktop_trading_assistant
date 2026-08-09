package com.tradingassistant.portfolio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.tradingassistant.market.Market;
import com.tradingassistant.market.Exchange;
import com.tradingassistant.market.AssetType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository extends JpaRepository<PortfolioItem, UUID> {
    List<PortfolioItem> findAllByUserIdOrderBySortOrderAscCreatedAtAsc(UUID userId);
    List<PortfolioItem> findAllByUserIdAndMarketOrderBySortOrderAscCreatedAtAsc(UUID userId, Market market);
    List<PortfolioItem> findAllByMarketOrderByCreatedAtAsc(Market market);
    Optional<PortfolioItem> findByIdAndUserId(UUID id, UUID userId);
    Optional<PortfolioItem> findByUserIdAndExchangeAndSymbolAndAssetType(
            UUID userId, Exchange exchange, String symbol, AssetType assetType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from PortfolioItem item where item.id = :id and item.userId = :userId")
    Optional<PortfolioItem> findLockedByIdAndUserId(@Param("id") UUID id,
            @Param("userId") UUID userId);

    @Query(value = "select id from users where id = :userId for update", nativeQuery = true)
    UUID lockUser(@Param("userId") UUID userId);
    long countByUserId(UUID userId);
}
