package com.tradingassistant.quote;

import com.tradingassistant.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
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
                .orElseThrow(() -> new QuoteUnavailableException("当前没有可用行情源"));
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
        return snapshots(instruments, QuoteRequestOptions.DEFAULT);
    }

    public List<Quote> snapshots(List<InstrumentId> instruments, QuoteRequestOptions options) {
        if (instruments.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        return execute(provider -> {
            return provider.snapshots(instruments, options == null ? QuoteRequestOptions.DEFAULT : options);
        }, "snapshots").stream()
                .map(quote -> {
                    boolean expired = quote.sourceTimestamp() == null
                            || Duration.between(quote.sourceTimestamp(), now).toSeconds()
                            > properties.quotes().staleSeconds();
                    return quote.withMarketState(marketPhase(now), quote.stale() || expired);
                }).toList();
    }

    private static String marketPhase(Instant now) {
        ZonedDateTime local = now.atZone(ZoneId.of("Asia/Shanghai"));
        DayOfWeek day = local.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return "CLOSED";
        LocalTime time = local.toLocalTime();
        if (time.isBefore(LocalTime.of(9, 15))) return "PRE_OPEN";
        if (time.isBefore(LocalTime.of(9, 30))) return "AUCTION";
        if (time.isBefore(LocalTime.of(11, 30))) return "CONTINUOUS";
        if (time.isBefore(LocalTime.of(13, 0))) return "BREAK";
        if (time.isBefore(LocalTime.of(15, 0))) return "CONTINUOUS";
        return "CLOSED";
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
        throw new QuoteUnavailableException("当前没有可用行情源", last);
    }

    public record ProviderStatus(String id, int priority, boolean healthy, boolean demo,
                                 QuoteProvider.Capabilities capabilities) {}
}
