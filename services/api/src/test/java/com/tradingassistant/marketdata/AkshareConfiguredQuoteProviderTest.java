package com.tradingassistant.marketdata;

import com.tradingassistant.config.AppProperties;
import com.tradingassistant.quote.InstrumentId;
import com.tradingassistant.quote.Quote;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AkshareConfiguredQuoteProviderTest {
    @Test
    void snapshotModeReadsOnlyRedis() {
        var configs = mock(MarketDataConfigService.class);
        var redis = mock(RedisMarketSnapshotRepository.class);
        var gateway = mock(AkshareGatewayClient.class);
        when(configs.current()).thenReturn(MarketDataConfig.defaults());
        var instrument = InstrumentId.parse("600519");
        Quote quote = mock(Quote.class);
        when(redis.find(MarketDataConfig.SnapshotSource.EASTMONEY, List.of(instrument)))
                .thenReturn(List.of(quote));
        var provider = new AkshareConfiguredQuoteProvider(configs, redis, gateway, properties());

        assertThat(provider.snapshots(List.of(instrument))).containsExactly(quote);
        verifyNoInteractions(gateway);
    }

    @Test
    void eastmoneySingleModeSkipsBseWithoutDroppingSupportedStocks() {
        var configs = mock(MarketDataConfigService.class);
        var redis = mock(RedisMarketSnapshotRepository.class);
        var gateway = mock(AkshareGatewayClient.class);
        MarketDataConfig config = MarketDataConfig.defaults();
        config.update(MarketDataConfig.Provider.AKSHARE, MarketDataConfig.Mode.SINGLE_STOCK,
                MarketDataConfig.SnapshotSource.EASTMONEY,
                MarketDataConfig.SingleSource.EASTMONEY, 10);
        when(configs.current()).thenReturn(config);
        var provider = new AkshareConfiguredQuoteProvider(configs, redis, gateway, properties());

        InstrumentId shanghai = InstrumentId.parse("SSE:600519");
        InstrumentId beijing = InstrumentId.parse("BSE:830799");
        Quote quote = mock(Quote.class);
        when(gateway.singleQuotes(MarketDataConfig.SingleSource.EASTMONEY, List.of(shanghai)))
                .thenReturn(List.of(quote));

        assertThat(provider.snapshots(List.of(shanghai, beijing))).containsExactly(quote);
        assertThat(provider.snapshots(List.of(beijing))).isEmpty();
        assertThat(provider.exchanges()).doesNotContain(InstrumentId.Exchange.BSE);
        verify(gateway).singleQuotes(MarketDataConfig.SingleSource.EASTMONEY, List.of(shanghai));
    }

    private AppProperties properties() {
        return new AppProperties(null, null, new AppProperties.Quotes(30, 2000,
                new AppProperties.Quotes.HttpProvider(true, "http://127.0.0.1:8090", "key", 10)));
    }
}
