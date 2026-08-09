package com.tradingassistant.market;

public enum Exchange {
    SSE("\\d{6}"),
    SZSE("\\d{6}"),
    BSE("\\d{6}"),
    HKEX("\\d{5}"),
    NASDAQ("[A-Z][A-Z0-9.-]{0,9}"),
    NYSE("[A-Z][A-Z0-9.-]{0,9}"),
    AMEX("[A-Z][A-Z0-9.-]{0,9}"),
    CN_FUND("\\d{6}"),
    SSE_INDEX("\\d{6}"),
    SZSE_INDEX("\\d{6}"),
    BSE_INDEX("\\d{6}"),
    CSI_INDEX("\\d{6}");

    private final String codePattern;

    Exchange(String codePattern) {
        this.codePattern = codePattern;
    }

    public boolean accepts(String code) {
        return code != null && code.matches(codePattern);
    }
}
