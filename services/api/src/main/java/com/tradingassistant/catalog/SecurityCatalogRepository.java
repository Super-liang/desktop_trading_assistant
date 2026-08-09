package com.tradingassistant.catalog;

import java.util.List;
import com.tradingassistant.market.Market;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SecurityCatalogRepository extends JpaRepository<SecurityCatalogItem, String> {
    long countByMarket(Market market);
    List<SecurityCatalogItem> findAllByMarket(Market market);
    List<SecurityCatalogItem> findAllByMarketAndStatus(
            Market market, SecurityCatalogItem.Status status);

    @Query("""
            select security from SecurityCatalogItem security
            where security.status = com.tradingassistant.catalog.SecurityCatalogItem.Status.ACTIVE
              and (security.code like concat(:keyword, '%')
                   or lower(security.name) like lower(concat('%', :keyword, '%')))
            order by case when security.code = :keyword then 0 else 1 end, security.code
            """)
    List<SecurityCatalogItem> search(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
            select security from SecurityCatalogItem security
            where security.market = :market
              and security.status = com.tradingassistant.catalog.SecurityCatalogItem.Status.ACTIVE
              and (security.code like concat(:keyword, '%')
                   or lower(security.name) like lower(concat('%', :keyword, '%')))
            order by case when security.code = :keyword then 0 else 1 end, security.code
            """)
    List<SecurityCatalogItem> searchByMarket(@Param("market") Market market,
            @Param("keyword") String keyword, Pageable pageable);
}
