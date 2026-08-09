package com.tradingassistant.catalog;

import com.tradingassistant.market.Market;
import com.tradingassistant.marketdata.AkshareGatewayClient;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.quotes.http.enabled", havingValue = "true")
public class FundNavSyncService {
    private static final Logger log = LoggerFactory.getLogger(FundNavSyncService.class);
    private final SecurityCatalogRepository catalog;
    private final FundNavQuoteRepository navQuotes;
    private final AkshareGatewayClient gateway;

    public FundNavSyncService(SecurityCatalogRepository catalog,
            FundNavQuoteRepository navQuotes, AkshareGatewayClient gateway) {
        this.catalog = catalog;
        this.navQuotes = navQuotes;
        this.gateway = gateway;
    }

    public void synchronize() {
        Set<String> activeFunds = new HashSet<>(catalog.findAllByMarketAndStatus(
                Market.PUBLIC_FUND, SecurityCatalogItem.Status.ACTIVE).stream()
                .map(SecurityCatalogItem::getInstrumentId).toList());
        if (activeFunds.isEmpty()) {
            log.info("跳过基金单位净值同步：开放式基金目录为空");
            return;
        }
        List<FundNavQuote> updates = new ArrayList<>();
        for (AkshareGatewayClient.FundNav row : gateway.allFundUnitNav()) {
            if (!activeFunds.contains(row.instrumentId())) continue;
            updates.add(new FundNavQuote(row.instrumentId(), row.navDate(), row.unitNav(),
                    row.source(), row.sourceUpdatedAt()));
            if (row.previousNavDate() != null && row.previousUnitNav() != null) {
                updates.add(new FundNavQuote(row.instrumentId(), row.previousNavDate(),
                        row.previousUnitNav(), row.source(), row.sourceUpdatedAt()));
            }
        }
        if (updates.isEmpty()) {
            throw new IllegalStateException("AKShare 开放式基金单位净值为空");
        }
        // 复合主键使同一基金同一净值日期重复执行时只更新、不产生重复记录。
        navQuotes.saveAll(updates);
        log.info("开放式基金单位净值同步成功：rowCount={}", updates.size());
    }
}
