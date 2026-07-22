package com.tradingassistant;

import com.tradingassistant.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties(AppProperties.class)
public class TradingAssistantApplication {
    public static void main(String[] args) {
        SpringApplication.run(TradingAssistantApplication.class, args);
    }
}
