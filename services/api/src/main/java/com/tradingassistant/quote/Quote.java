package com.tradingassistant.quote;

import java.math.BigDecimal;
import java.time.Instant;

public record Quote(
        String instrumentId,
        String name,
        BigDecimal last,
        BigDecimal previousClose,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal change,
        BigDecimal changePercent,
        BigDecimal volume,
        String marketPhase,
        String source,
        Instant sourceTimestamp,
        Instant receivedAt,
        boolean delayed,
        boolean stale,
        boolean demo
) {
    public Quote withStale(boolean value) {
        return new Quote(instrumentId, name, last, previousClose, open, high, low, change,
                changePercent, volume, marketPhase, source, sourceTimestamp, receivedAt,
                delayed, value, demo);
    }

    public Quote withMarketState(String phase, boolean staleValue) {
        return new Quote(instrumentId, name, last, previousClose, open, high, low, change,
                changePercent, volume, phase, source, sourceTimestamp, receivedAt,
                delayed, staleValue, demo);
    }
}
