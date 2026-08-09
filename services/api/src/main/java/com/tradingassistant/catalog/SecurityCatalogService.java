package com.tradingassistant.catalog;

import com.tradingassistant.quote.InstrumentId;
import com.tradingassistant.market.AssetType;
import com.tradingassistant.market.Currency;
import com.tradingassistant.market.Exchange;
import com.tradingassistant.market.Market;
import com.tradingassistant.market.InstrumentKey;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class SecurityCatalogService {
    private final SecurityCatalogRepository repository;

    public SecurityCatalogService(SecurityCatalogRepository repository) {
        this.repository = repository;
    }

    public List<View> search(Market market, String query, int limit) {
        String keyword = query == null ? "" : query.strip();
        if (keyword.isEmpty()) return List.of();
        if (repository.countByMarket(market) == 0) {
            throw new IllegalStateException(market + " 证券目录正在准备中，请稍后重试");
        }
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return repository.searchByMarket(market, keyword, PageRequest.of(0, safeLimit)).stream()
                .map(View::from).toList();
    }

    public List<View> search(String query, int limit) {
        return search(Market.A_SHARE, query, limit);
    }

    public SecurityCatalogItem requireActive(InstrumentId instrument) {
        return repository.findById(instrument.canonical())
                .filter(item -> item.getStatus() == SecurityCatalogItem.Status.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("证券不在当前 A 股目录中"));
    }

    public SecurityCatalogItem requireActive(Market market, InstrumentKey instrument) {
        return repository.findById(instrument.canonical())
                .filter(item -> item.getStatus() == SecurityCatalogItem.Status.ACTIVE)
                .filter(item -> item.getMarket() == market)
                .orElseThrow(() -> new IllegalArgumentException("证券不在所选市场的有效目录中"));
    }

    public boolean isEmpty() { return repository.count() == 0; }
    public boolean isEmpty(Market market) { return repository.countByMarket(market) == 0; }
    public record View(String instrumentId, String code, String name, Market market,
            Exchange exchange, Currency currency, AssetType assetType) {
        static View from(SecurityCatalogItem item) {
            return new View(item.getInstrumentId(), item.getCode(), item.getName(), item.getMarket(),
                    item.getExchange(), item.getCurrency(), item.getAssetType());
        }
    }
}
