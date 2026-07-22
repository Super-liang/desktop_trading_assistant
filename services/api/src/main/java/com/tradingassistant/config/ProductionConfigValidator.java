package com.tradingassistant.config;

import java.util.Arrays;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProductionConfigValidator implements ApplicationRunner {
    private static final String DEV_SECRET = "dev-only-secret-must-be-at-least-32-bytes-long";
    private static final String DEV_DATABASE_PASSWORD = "change-me-for-production";
    private final Environment environment;
    private final AppProperties properties;

    public ProductionConfigValidator(Environment environment, AppProperties properties) {
        this.environment = environment;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (!production) {
            return;
        }
        if (properties.jwt() == null || properties.jwt().secret() == null
                || properties.jwt().secret().isBlank()
                || DEV_SECRET.equals(properties.jwt().secret())) {
            throw new IllegalStateException("生产环境必须配置独立的 JWT_SECRET");
        }
        requireText("spring.datasource.url", "生产环境必须配置 PostgreSQL JDBC 地址");
        if (!environment.getProperty("spring.datasource.url", "").startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException("生产环境数据库必须使用 PostgreSQL");
        }
        requireText("spring.datasource.username", "生产环境必须配置数据库用户名");
        String databasePassword = environment.getProperty("spring.datasource.password", "");
        if (databasePassword.isBlank() || DEV_DATABASE_PASSWORD.equals(databasePassword)) {
            throw new IllegalStateException("生产环境必须配置独立的数据库密码");
        }
        String bindAddress = environment.getProperty("server.address", "");
        if (!java.util.Set.of("127.0.0.1", "::1", "localhost").contains(bindAddress)) {
            throw new IllegalStateException("单服务器生产模式 API 必须绑定回环地址");
        }
        if (properties.quotes() != null && properties.quotes().http().enabled()) {
            String quoteKey = properties.quotes().http().apiKey();
            if (quoteKey == null || quoteKey.isBlank()) {
                throw new IllegalStateException("启用 HTTP Provider 时必须配置行情共享密钥");
            }
            String baseUrl = properties.quotes().http().baseUrl();
            if (baseUrl == null || !baseUrl.startsWith("http://127.0.0.1:")) {
                throw new IllegalStateException("AKShare 网关必须通过回环地址访问");
            }
        }
        if (environment.getProperty("app.quotes.demo-enabled", Boolean.class, false)) {
            throw new IllegalStateException("生产环境禁止启用 DEMO 行情源");
        }
        String redisHost = environment.getProperty("spring.data.redis.host", "");
        if (!java.util.Set.of("127.0.0.1", "::1", "localhost").contains(redisHost)) {
            throw new IllegalStateException("单服务器生产模式 Redis 必须绑定回环地址");
        }
        requireText("spring.data.redis.password", "生产环境 Redis 必须配置访问密码");
        if (properties.admin() != null && properties.admin().password() != null
                && properties.admin().password().contains("ChangeMe")) {
            throw new IllegalStateException("生产环境禁止使用演示管理员密码");
        }
    }

    private void requireText(String key, String message) {
        String value = environment.getProperty(key, "");
        if (value.isBlank()) {
            throw new IllegalStateException(message);
        }
    }
}
