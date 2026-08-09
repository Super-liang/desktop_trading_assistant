package com.tradingassistant.market;

import java.util.List;

public record MarketIndex(InstrumentKey instrument, String displayName) {
    private static final List<MarketIndex> DASHBOARD = List.of(
            new MarketIndex(new InstrumentKey(Exchange.SSE_INDEX, "000001"), "上证指数"),
            new MarketIndex(new InstrumentKey(Exchange.SZSE_INDEX, "399001"), "深证成指"),
            new MarketIndex(new InstrumentKey(Exchange.SZSE_INDEX, "399006"), "创业板指"),
            new MarketIndex(new InstrumentKey(Exchange.BSE_INDEX, "899050"), "北证50"),
            new MarketIndex(new InstrumentKey(Exchange.SSE_INDEX, "000680"), "科创综指"),
            new MarketIndex(new InstrumentKey(Exchange.SSE_INDEX, "000688"), "科创50"),
            new MarketIndex(new InstrumentKey(Exchange.CSI_INDEX, "000300"), "沪深300")
    );

    public static List<MarketIndex> dashboardIndices() {
        return DASHBOARD;
    }
}
