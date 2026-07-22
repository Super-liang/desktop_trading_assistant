package com.tradingassistant.catalog;

import com.tradingassistant.quote.InstrumentId;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class SecurityCatalogService {
    private final SecurityCatalogRepository repository;

    public SecurityCatalogService(SecurityCatalogRepository repository) {
        this.repository = repository;
    }

    public List<View> search(String query, int limit) {
        String keyword = query == null ? "" : query.strip();
        if (keyword.isEmpty()) return List.of();
        if (repository.count() == 0) {
            throw new IllegalStateException("A 股证券目录正在准备中，请稍后重试");
        }
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return repository.search(keyword, PageRequest.of(0, safeLimit)).stream()
                .map(View::from).toList();
    }

    public SecurityCatalogItem requireActive(InstrumentId instrument) {
        return repository.findById(instrument.canonical())
                .filter(item -> item.getStatus() == SecurityCatalogItem.Status.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("证券不在当前 A 股目录中"));
    }

    public boolean isEmpty() { return repository.count() == 0; }
    public record View(String instrumentId, String code, String name,
            InstrumentId.Exchange exchange, InstrumentId.AssetType assetType) {
        static View from(SecurityCatalogItem item) {
            return new View(item.getInstrumentId(), item.getCode(), item.getName(),
                    item.getExchange(), item.getAssetType());
        }
    }
}
