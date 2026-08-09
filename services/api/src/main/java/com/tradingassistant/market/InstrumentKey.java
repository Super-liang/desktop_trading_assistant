package com.tradingassistant.market;

import java.util.Locale;

public record InstrumentKey(Exchange exchange, String code) {
    public InstrumentKey {
        if (exchange == null) throw new IllegalArgumentException("交易所不能为空");
        code = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
        if (!exchange.accepts(code)) throw new IllegalArgumentException("证券代码与交易所不匹配");
    }

    public String canonical() {
        return exchange + ":" + code;
    }
}
