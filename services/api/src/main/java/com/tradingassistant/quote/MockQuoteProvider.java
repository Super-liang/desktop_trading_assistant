package com.tradingassistant.quote;

import com.tradingassistant.config.AppProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.quotes.demo-enabled", havingValue = "true")
public class MockQuoteProvider implements QuoteProvider {
    private static final ZoneId CHINA = ZoneId.of("Asia/Shanghai");
    private static final List<Seed> SEEDS = List.of(
            new Seed("SSE:600000", "浦发银行", "10.25"),
            new Seed("SSE:600519", "贵州茅台", "1468.00"),
            new Seed("SSE:510300", "沪深300ETF", "4.120"),
            new Seed("SZSE:000001", "平安银行", "11.30"),
            new Seed("SZSE:000858", "五粮液", "128.50"),
            new Seed("SZSE:300750", "宁德时代", "245.80"),
            new Seed("BSE:920001", "北交示例", "18.60"));
    private final AppProperties properties;

    public MockQuoteProvider(AppProperties properties) {
        this.properties = properties;
    }

    @Override public String id() { return "DEMO"; }
    @Override public int priority() { return 1000; }
    @Override public boolean healthy() { return true; }
    @Override public boolean demo() { return true; }
    @Override public Set<InstrumentId.Exchange> exchanges() {
        return EnumSet.allOf(InstrumentId.Exchange.class);
    }

    @Override
    public List<InstrumentSearchResult> search(String query) {
        String keyword = query == null ? "" : query.strip().toUpperCase(Locale.ROOT);
        return SEEDS.stream()
                .filter(seed -> keyword.isBlank() || seed.id().contains(keyword)
                        || seed.name().contains(keyword))
                .map(seed -> {
                    InstrumentId id = InstrumentId.parse(seed.id());
                    return new InstrumentSearchResult(id.canonical(), id.code(), seed.name(),
                            id.exchange(), id.assetType());
                }).limit(20).toList();
    }

    @Override
    public List<Quote> snapshots(List<InstrumentId> instruments) {
        Instant now = Instant.now();
        long tick = Math.max(1, now.toEpochMilli() / Math.max(500, properties.quotes().tickMillis()));
        return instruments.stream().map(id -> quote(id, tick, now)).toList();
    }

    private Quote quote(InstrumentId id, long tick, Instant now) {
        Seed seed = SEEDS.stream().filter(value -> value.id().equals(id.canonical())).findFirst()
                .orElse(new Seed(id.canonical(), id.code(), "10.00"));
        BigDecimal previous = new BigDecimal(seed.base());
        double wave = Math.sin((tick + id.code().hashCode()) * 0.17) * 0.018;
        BigDecimal last = previous.multiply(BigDecimal.valueOf(1 + wave))
                .setScale(Math.max(2, previous.scale()), RoundingMode.HALF_UP);
        BigDecimal change = last.subtract(previous);
        BigDecimal percent = change.multiply(BigDecimal.valueOf(100))
                .divide(previous, 4, RoundingMode.HALF_UP);
        BigDecimal spread = previous.multiply(new BigDecimal("0.012"));
        return new Quote(id.canonical(), seed.name(), last, previous,
                previous.add(spread.multiply(new BigDecimal("0.1"))),
                previous.add(spread), previous.subtract(spread), change, percent,
                BigDecimal.valueOf(Math.abs(id.code().hashCode() % 900_000) + tick % 100_000),
                marketPhase(), id(), now, Instant.now(), false, false, true);
    }

    private String marketPhase() {
        LocalTime time = LocalTime.now(CHINA);
        if (time.isBefore(LocalTime.of(9, 15))) return "PRE_OPEN";
        if (time.isBefore(LocalTime.of(9, 30))) return "AUCTION";
        if (time.isBefore(LocalTime.of(11, 30))) return "CONTINUOUS";
        if (time.isBefore(LocalTime.of(13, 0))) return "BREAK";
        if (time.isBefore(LocalTime.of(15, 0))) return "CONTINUOUS";
        return "CLOSED";
    }

    private record Seed(String id, String name, String base) {}
}
