package com.tradingassistant.quote;

import com.tradingassistant.config.AppProperties;
import java.util.List;
import java.util.Set;
import java.time.Instant;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MockQuoteProviderTest {
    @Test
    void returnsTraceableDemoQuote() {
        var provider = new MockQuoteProvider(new AppProperties(
                null, null, quotesProperties()));
        Quote quote = provider.snapshots(List.of(InstrumentId.parse("600000"))).get(0);
        assertThat(quote.source()).isEqualTo("DEMO");
        assertThat(quote.demo()).isTrue();
        assertThat(quote.last()).isPositive();
        assertThat(quote.sourceTimestamp()).isNotNull();
    }

    @Test
    void fallsBackAfterProviderFailureAndMarksOldQuoteStale() {
        AppProperties properties = new AppProperties(
                null, null, quotesProperties());
        QuoteProvider failing = new StubProvider("PRIMARY", 1, true) {
            @Override public List<Quote> snapshots(List<InstrumentId> instruments) {
                throw new IllegalStateException("provider unavailable");
            }
        };
        QuoteProvider oldBackup = new StubProvider("BACKUP", 2, true) {
            @Override public List<Quote> snapshots(List<InstrumentId> instruments) {
                InstrumentId id = instruments.get(0);
                Instant old = Instant.now().minusSeconds(60);
                return List.of(new Quote(id.canonical(), "备用行情", BigDecimal.TEN,
                        BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, "CONTINUOUS",
                        id(), old, Instant.now(), false, false, false));
            }
        };
        QuoteProviderRegistry registry = new QuoteProviderRegistry(
                List.of(failing, oldBackup), properties);
        Quote quote = registry.snapshots(List.of(InstrumentId.parse("600000"))).get(0);
        assertThat(quote.source()).isEqualTo("BACKUP");
        assertThat(quote.stale()).isTrue();
    }

    @Test
    void emptyRequestDoesNotCallProviderAndPartialResponseFallsBack() {
        AppProperties properties = new AppProperties(
                null, null, quotesProperties());
        QuoteProvider partial = new StubProvider("PARTIAL", 1, true);
        QuoteProvider complete = new MockQuoteProvider(properties);
        QuoteProviderRegistry registry = new QuoteProviderRegistry(
                List.of(partial, complete), properties);

        assertThat(registry.snapshots(List.of())).isEmpty();
        assertThat(registry.snapshots(List.of(InstrumentId.parse("600000"))))
                .singleElement().extracting(Quote::source).isEqualTo("DEMO");
    }

    private static class StubProvider implements QuoteProvider {
        private final String id;
        private final int priority;
        private final boolean healthy;
        StubProvider(String id, int priority, boolean healthy) {
            this.id = id; this.priority = priority; this.healthy = healthy;
        }
        @Override public String id() { return id; }
        @Override public int priority() { return priority; }
        @Override public boolean healthy() { return healthy; }
        @Override public boolean demo() { return false; }
        @Override public Set<InstrumentId.Exchange> exchanges() {
            return Set.of(InstrumentId.Exchange.SSE);
        }
        @Override public List<InstrumentSearchResult> search(String query) { return List.of(); }
        @Override public List<Quote> snapshots(List<InstrumentId> instruments) { return List.of(); }
    }

    private static AppProperties.Quotes quotesProperties() {
        return new AppProperties.Quotes(15, 2000,
                new AppProperties.Quotes.HttpProvider(false, "", "", 10));
    }
}
