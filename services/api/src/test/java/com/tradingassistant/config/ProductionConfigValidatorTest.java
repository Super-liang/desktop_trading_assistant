package com.tradingassistant.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigValidatorTest {
    private static final String STRONG_JWT = "production-jwt-secret-with-at-least-32-bytes";
    private static final String STRONG_QUOTE_KEY = "production-quote-key-with-32-bytes";

    @Test
    void rejectsDefaultDatabasePasswordInProduction() {
        var validator = validator(baseEnvironment()
                        .withProperty("spring.datasource.password", "change-me-for-production"),
                properties(STRONG_JWT, false, ""));

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("数据库密码");
    }

    @Test
    void rejectsMissingQuoteKeyWhenHttpProviderIsEnabled() {
        var validator = validator(baseEnvironment(),
                properties(STRONG_JWT, true, ""));

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("行情共享密钥");
    }

    @Test
    void rejectsPublicBindAddressInSingleServerProductionProfile() {
        var validator = validator(baseEnvironment()
                        .withProperty("server.address", "0.0.0.0"),
                properties(STRONG_JWT, true, STRONG_QUOTE_KEY));

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("回环地址");
    }

    @Test
    void acceptsCompleteLoopbackProductionConfiguration() {
        var validator = validator(baseEnvironment(),
                properties(STRONG_JWT, true, STRONG_QUOTE_KEY));

        assertThatCode(() -> validator.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDemoQuoteProviderInProduction() {
        var validator = validator(baseEnvironment().withProperty("app.quotes.demo-enabled", "true"),
                properties(STRONG_JWT, true, STRONG_QUOTE_KEY));

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEMO");
    }

    private MockEnvironment baseEnvironment() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("spring.datasource.url",
                "jdbc:postgresql://127.0.0.1:5433/trading");
        environment.setProperty("spring.datasource.username", "trading");
        environment.setProperty("spring.datasource.password", "strong-database-password");
        environment.setProperty("server.address", "127.0.0.1");
        environment.setProperty("spring.data.redis.host", "127.0.0.1");
        environment.setProperty("spring.data.redis.password", "strong-redis-password");
        return environment;
    }

    private ProductionConfigValidator validator(
            MockEnvironment environment, AppProperties properties) {
        return new ProductionConfigValidator(environment, properties);
    }

    private AppProperties properties(String jwt, boolean httpEnabled, String quoteKey) {
        return new AppProperties(
                new AppProperties.Jwt(jwt, 15, 30),
                new AppProperties.Admin("", ""),
                new AppProperties.Quotes(30, 2000,
                        new AppProperties.Quotes.HttpProvider(
                                httpEnabled, "http://127.0.0.1:8090", quoteKey, 10)));
    }
}
