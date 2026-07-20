package com.tradingassistant.config;

import java.util.Arrays;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProductionConfigValidator implements ApplicationRunner {
    private static final String DEV_SECRET = "dev-only-secret-must-be-at-least-32-bytes-long";
    private final Environment environment;
    private final AppProperties properties;

    public ProductionConfigValidator(Environment environment, AppProperties properties) {
        this.environment = environment;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (production && DEV_SECRET.equals(properties.jwt().secret())) {
            throw new IllegalStateException("生产环境必须配置独立的 JWT_SECRET");
        }
        if (production && properties.admin().password() != null
                && properties.admin().password().contains("ChangeMe")) {
            throw new IllegalStateException("生产环境禁止使用演示管理员密码");
        }
    }
}
