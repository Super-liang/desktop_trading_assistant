package com.tradingassistant.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class AppPropertiesBindingTest {
    @Test
    void bindsHttpQuoteProviderFromProductionProperties() {
        var source = new MapConfigurationPropertySource(Map.of(
                "app.jwt.secret", "production-jwt-secret-at-least-32-bytes",
                "app.jwt.access-minutes", "15",
                "app.jwt.refresh-days", "30",
                "app.quotes.stale-seconds", "30",
                "app.quotes.tick-millis", "2000",
                "app.quotes.http.enabled", "true",
                "app.quotes.http.base-url", "http://127.0.0.1:8090",
                "app.quotes.http.api-key", "production-shared-key",
                "app.quotes.http.priority", "10"));

        AppProperties properties = new Binder(source)
                .bind("app", Bindable.of(AppProperties.class))
                .orElseThrow(() -> new AssertionError("AppProperties 未绑定"));

        assertThat(properties.quotes().http().enabled()).isTrue();
        assertThat(properties.quotes().http().baseUrl()).isEqualTo("http://127.0.0.1:8090");
        assertThat(properties.quotes().http().apiKey()).isEqualTo("production-shared-key");
    }
}
