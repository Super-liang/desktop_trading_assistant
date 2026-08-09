package com.tradingassistant.marketdata;

import com.tradingassistant.market.MarketClock;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MarketDataStatusControllerTest {
    @Test
    void singleModeStatusUsesRequestSourceInsteadOfGlobalDefault() {
        var configs = mock(MarketDataConfigService.class);
        var snapshots = mock(RedisMarketSnapshotRepository.class);
        var gateway = mock(AkshareGatewayClient.class);
        var marketClock = mock(MarketClock.class);
        MarketDataConfig config = MarketDataConfig.defaults();
        config.update(MarketDataConfig.Provider.AKSHARE, MarketDataConfig.Mode.SINGLE_STOCK,
                MarketDataConfig.SnapshotSource.EASTMONEY,
                MarketDataConfig.SingleSource.EASTMONEY, 30);
        when(configs.current()).thenReturn(config);
        when(gateway.health()).thenReturn(new AkshareGatewayClient.GatewayHealth("UP", "AKSHARE"));
        when(gateway.sourceStatus()).thenReturn(List.of());
        var controller = new MarketDataStatusController(configs, snapshots, gateway, marketClock);

        var status = controller.status(MarketDataConfig.Mode.SINGLE_STOCK,
                MarketDataConfig.SingleSource.XUEQIU);

        assertThat(status.components()).extracting(MarketDataStatusController.ComponentStatus::label)
                .contains("SINGLE_XUEQIU")
                .doesNotContain("SINGLE_EASTMONEY");
        assertThat(config.getSingleSource()).isEqualTo(MarketDataConfig.SingleSource.EASTMONEY);
    }

    @Test
    void requestModeOverridesGlobalDefaultForStatusComponents() {
        var configs = mock(MarketDataConfigService.class);
        var snapshots = mock(RedisMarketSnapshotRepository.class);
        var gateway = mock(AkshareGatewayClient.class);
        var marketClock = mock(MarketClock.class);
        MarketDataConfig config = MarketDataConfig.defaults();
        config.update(MarketDataConfig.Provider.AKSHARE, MarketDataConfig.Mode.SINGLE_STOCK,
                MarketDataConfig.SnapshotSource.EASTMONEY,
                MarketDataConfig.SingleSource.EASTMONEY, 30);
        when(configs.current()).thenReturn(config);
        when(gateway.health()).thenReturn(new AkshareGatewayClient.GatewayHealth("UP", "AKSHARE"));
        when(gateway.sourceStatus()).thenReturn(List.of());
        when(snapshots.ping()).thenReturn(true);
        var controller = new MarketDataStatusController(configs, snapshots, gateway, marketClock);

        var status = controller.status(MarketDataConfig.Mode.MARKET_SNAPSHOT, null);

        assertThat(status.mode()).isEqualTo(MarketDataConfig.Mode.MARKET_SNAPSHOT);
        assertThat(status.components()).extracting(MarketDataStatusController.ComponentStatus::label)
                .contains("A股新浪缓存", "港股新浪缓存", "美股新浪缓存",
                        "A_SHARE:SNAPSHOT:SINA", "HK_STOCK:SNAPSHOT:SINA",
                        "US_STOCK:POSITION:SINA")
                .doesNotContain("SINGLE_EASTMONEY");
    }
}
