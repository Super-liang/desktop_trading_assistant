package com.tradingassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Admin admin,
        Quotes quotes
) {
    public AppProperties {
        if (quotes == null) {
            quotes = new Quotes(15, 2000);
        }
    }
    public record Jwt(String secret, long accessMinutes, long refreshDays) {}
    public record Admin(String email, String password) {}
    public record Quotes(long staleSeconds, long tickMillis, HttpProvider http) {
        public Quotes {
            if (http == null) {
                http = new HttpProvider(false, "", "", 10);
            }
        }
        public Quotes(long staleSeconds, long tickMillis) {
            this(staleSeconds, tickMillis, new HttpProvider(false, "", "", 10));
        }
        public record HttpProvider(boolean enabled, String baseUrl, String apiKey, int priority) {}
    }
}
