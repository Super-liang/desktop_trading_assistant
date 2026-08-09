package com.tradingassistant.market;

import java.time.ZoneId;

public enum Market {
    A_SHARE(Currency.CNY, ZoneId.of("Asia/Shanghai"), null),
    HK_STOCK(Currency.HKD, ZoneId.of("Asia/Hong_Kong"), Exchange.HKEX),
    US_STOCK(Currency.USD, ZoneId.of("America/New_York"), null),
    PUBLIC_FUND(Currency.CNY, ZoneId.of("Asia/Shanghai"), Exchange.CN_FUND);

    private final Currency currency;
    private final ZoneId timezone;
    private final Exchange exchange;

    Market(Currency currency, ZoneId timezone, Exchange exchange) {
        this.currency = currency;
        this.timezone = timezone;
        this.exchange = exchange;
    }

    public Currency currency() { return currency; }
    public ZoneId timezone() { return timezone; }
    public Exchange exchange() { return exchange; }
}
