package com.tradingassistant.market;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketDomainTest {
    @Test
    void exposesStableMarketMetadata() {
        assertThat(Market.A_SHARE.currency()).isEqualTo(Currency.CNY);
        assertThat(Market.HK_STOCK.timezone().getId()).isEqualTo("Asia/Hong_Kong");
        assertThat(Market.US_STOCK.currency()).isEqualTo(Currency.USD);
        assertThat(Market.PUBLIC_FUND.exchange()).isEqualTo(Exchange.CN_FUND);
    }

    @Test
    void keepsTheSevenDashboardIndicesInProductOrder() {
        assertThat(MarketIndex.dashboardIndices())
                .extracting(index -> index.instrument().canonical())
                .containsExactly(
                        "SSE_INDEX:000001",
                        "SZSE_INDEX:399001",
                        "SZSE_INDEX:399006",
                        "BSE_INDEX:899050",
                        "SSE_INDEX:000680",
                        "SSE_INDEX:000688",
                        "CSI_INDEX:000300");
    }

    @Test
    void validatesInstrumentCodeByExchange() {
        assertThat(new InstrumentKey(Exchange.HKEX, "00700").canonical()).isEqualTo("HKEX:00700");
        assertThat(new InstrumentKey(Exchange.NASDAQ, "AAPL").canonical()).isEqualTo("NASDAQ:AAPL");
        assertThatThrownBy(() -> new InstrumentKey(Exchange.SSE, "AAPL"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
