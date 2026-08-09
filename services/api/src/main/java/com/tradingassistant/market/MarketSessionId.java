package com.tradingassistant.market;

import java.io.Serializable;
import java.time.LocalDate;

public record MarketSessionId(Market market, LocalDate tradingDate) implements Serializable {}
