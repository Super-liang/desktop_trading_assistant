package com.tradingassistant.market;

import java.time.Instant;

public record MarketStatus(
        Market market,
        MarketPhase phase,
        Instant nextOpenAt,
        Instant nextCloseAt,
        String calendarSource,
        Instant calendarSyncedAt,
        boolean calendarAvailable) {}
