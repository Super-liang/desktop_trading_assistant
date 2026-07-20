package com.tradingassistant.quote;

import com.tradingassistant.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class QuoteProviderRegistry {
    private static final Logger log = LoggerFactory.getLogger(QuoteProviderRegistry.class);
    private final List<QuoteProvider> providers;
    private final AppProperties properties;

    public QuoteProviderRegistry(List<QuoteProvider> providers, AppProperties properties) {
        this.providers = providers.stream()
                .sorted(Comparator.comparingInt(QuoteProvider::priority)).toList();
        this.properties = properties;
    }

    public QuoteProvider active() {
        return providers.stream().filter(QuoteProvider::healthy).findFirst()
                .orElseThrow(() -> new IllegalStateException("当前没有可用行情源"));
    }

    public List<ProviderStatus> status() {
        return providers.stream().map(provider -> new ProviderStatus(
                provider.id(), provider.priority(), provider.healthy(), provider.demo(),
                provider.capabilities())).toList();
    }

    public List<QuoteProvider.InstrumentSearchResult> search(String query) {
        return execute(provider -> provider.search(query), "search");
    }

    public List<Quote> snapshots(List<InstrumentId> instruments) {
        if (instruments.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        return execute(provider -> {
            List<Quote> result = provider.snapshots(instruments);
            HashSet<String> returned = result.stream().map(Quote::instrumentId)
                    .collect(java.util.stream.Collectors.toCollection(HashSet::new));
            boolean complete = instruments.stream().map(InstrumentId::canonical)
                    .allMatch(returned::contains);
            if (!complete) {
                throw new IllegalStateException("行情源返回不完整");
            }
            return result;
        }, "snapshots").stream()
                .map(quote -> {
                    boolean expired = quote.sourceTimestamp() == null
                            || Duration.between(quote.sourceTimestamp(), now).toSeconds()
                            > properties.quotes().staleSeconds();
                    return quote.withStale(quote.stale() || expired);
                }).toList();
    }

    private <T> T execute(Function<QuoteProvider, T> operation, String operationName) {
        RuntimeException last = null;
        for (QuoteProvider provider : providers) {
            if (!provider.healthy()) continue;
            try {
                return operation.apply(provider);
            } catch (RuntimeException exception) {
                last = exception;
                // 不记录用户自选和持仓，只记录供应商级故障。
                log.warn("行情源调用失败，尝试降级：provider={}, operation={}",
                        provider.id(), operationName);
            }
        }
        throw new IllegalStateException("当前没有可用行情源", last);
    }

    public record ProviderStatus(String id, int priority, boolean healthy, boolean demo,
                                 QuoteProvider.Capabilities capabilities) {}
}
