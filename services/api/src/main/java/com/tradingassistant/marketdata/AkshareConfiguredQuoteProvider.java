package com.tradingassistant.marketdata;

import com.tradingassistant.config.AppProperties;
import com.tradingassistant.quote.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.quotes.http.enabled", havingValue = "true")
public class AkshareConfiguredQuoteProvider implements QuoteProvider {
    private final MarketDataConfigService configService;
    private final RedisMarketSnapshotRepository snapshots;
    private final AkshareGatewayClient gateway;
    private final int priority;

    public AkshareConfiguredQuoteProvider(MarketDataConfigService configService,
            RedisMarketSnapshotRepository snapshots, AkshareGatewayClient gateway,
            AppProperties properties) {
        this.configService = configService;
        this.snapshots = snapshots;
        this.gateway = gateway;
        this.priority = properties.quotes().http().priority();
    }

    @Override public String id() { return "AKSHARE_CONFIGURED"; }
    @Override public int priority() { return priority; }
    @Override public boolean healthy() { return true; }
    @Override public boolean demo() { return false; }
    @Override public Set<InstrumentId.Exchange> exchanges() {
        MarketDataConfig config = configService.current();
        if (config.getMode() == MarketDataConfig.Mode.SINGLE_STOCK
                && config.getSingleSource() == MarketDataConfig.SingleSource.EASTMONEY) {
            // AKShare stock_bid_ask_em 只区分沪/深 market id，不能安全查询北交所。
            return EnumSet.of(InstrumentId.Exchange.SSE, InstrumentId.Exchange.SZSE);
        }
        return EnumSet.allOf(InstrumentId.Exchange.class);
    }

    @Override
    public List<InstrumentSearchResult> search(String query) {
        MarketDataConfig config = configService.current();
        if (config.getMode() == MarketDataConfig.Mode.MARKET_SNAPSHOT) {
            return snapshots.search(config.getSnapshotSource(), query, 20).stream()
                    .map(value -> {
                        InstrumentId id = InstrumentId.parse(value.instrumentId());
                        return new InstrumentSearchResult(id.canonical(), id.code(), value.name(),
                                id.exchange(), id.assetType());
                    }).toList();
        }
        Set<InstrumentId.Exchange> supported = exchanges();
        return gateway.search(query).stream()
                .filter(item -> supported.contains(item.exchange()))
                .toList();
    }

    @Override
    public List<Quote> snapshots(List<InstrumentId> instruments) {
        MarketDataConfig config = configService.current();
        if (config.getMode() == MarketDataConfig.Mode.MARKET_SNAPSHOT) {
            return snapshots.find(config.getSnapshotSource(), instruments);
        }
        List<InstrumentId> supported = instruments;
        if (config.getSingleSource() == MarketDataConfig.SingleSource.EASTMONEY) {
            supported = instruments.stream()
                    .filter(item -> item.exchange() != InstrumentId.Exchange.BSE).toList();
        }
        if (supported.isEmpty()) return List.of();
        return gateway.singleQuotes(config.getSingleSource(), supported);
    }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(exchanges(), true, true, false, 50,
                "AKSHARE_RESEARCH_ONLY_NOT_FOR_COMMERCIAL_DISTRIBUTION");
    }
}
