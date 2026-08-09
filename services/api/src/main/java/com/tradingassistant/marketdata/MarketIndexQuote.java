package com.tradingassistant.marketdata;

import java.time.Instant;

public record MarketIndexQuote(
        String instrumentId,
        String code,
        String name,
        Double price,
        Double change,
        Double changePercent,
        Double open,
        Double previousClose,
        String source,
        Instant quoteAsOf,
        boolean available,
        Instant lastSuccessAt,
        boolean stale) {

    MarketIndexQuote withState(boolean currentAvailable, Instant successAt, boolean staleValue) {
        return new MarketIndexQuote(instrumentId, code, name, price, change, changePercent,
                open, previousClose, source, quoteAsOf, currentAvailable, successAt, staleValue);
    }
}
