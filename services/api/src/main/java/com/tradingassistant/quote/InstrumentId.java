package com.tradingassistant.quote;

import java.util.Locale;
import java.util.regex.Pattern;

public record InstrumentId(Exchange exchange, String code, AssetType assetType) {
    private static final Pattern SIX_DIGITS = Pattern.compile("\\d{6}");
    public enum Exchange { SSE, SZSE, BSE }
    public enum AssetType { STOCK, ETF }

    public InstrumentId {
        if (!SIX_DIGITS.matcher(code).matches()) {
            throw new IllegalArgumentException("A 股代码必须为 6 位数字");
        }
    }

    public static InstrumentId parse(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("股票代码不能为空");
        String value = input.strip().toUpperCase(Locale.ROOT).replace(".", ":");
        value = value.replaceFirst("^(SSE|SH):?", "SSE:")
                .replaceFirst("^(SZSE|SZ):?", "SZSE:")
                .replaceFirst("^(BSE|BJ):?", "BSE:");
        if (value.contains(":")) {
            String[] parts = value.split(":", 2);
            return new InstrumentId(Exchange.valueOf(parts[0]), parts[1], type(parts[1]));
        }
        if (!SIX_DIGITS.matcher(value).matches()) {
            throw new IllegalArgumentException("无法识别的 A 股代码");
        }
        Exchange exchange = switch (value.charAt(0)) {
            case '5', '6' -> Exchange.SSE;
            case '0', '1', '2', '3' -> Exchange.SZSE;
            case '4', '8', '9' -> Exchange.BSE;
            default -> throw new IllegalArgumentException("不支持的 A 股代码");
        };
        return new InstrumentId(exchange, value, type(value));
    }

    private static AssetType type(String code) {
        return code.startsWith("5") || code.startsWith("1") ? AssetType.ETF : AssetType.STOCK;
    }

    public String canonical() { return exchange + ":" + code; }
}
