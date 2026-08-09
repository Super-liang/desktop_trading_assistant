package com.tradingassistant.catalog;

import java.io.Serializable;
import java.time.LocalDate;

public record FundNavQuoteId(String instrumentId, LocalDate navDate) implements Serializable {}
