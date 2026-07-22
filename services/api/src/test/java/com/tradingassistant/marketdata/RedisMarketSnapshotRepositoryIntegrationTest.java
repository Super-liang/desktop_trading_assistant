package com.tradingassistant.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingassistant.quote.InstrumentId;
import com.tradingassistant.quote.Quote;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "REDIS_INTEGRATION", matches = "true")
class RedisMarketSnapshotRepositoryIntegrationTest {
    private static final String PREFIX = "trading:quotes:akshare:snapshot:";
    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;
    private RedisMarketSnapshotRepository repository;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                System.getenv().getOrDefault("REDIS_TEST_HOST", "127.0.0.1"),
                Integer.parseInt(System.getenv().getOrDefault("REDIS_TEST_PORT", "6389")));
        configuration.setPassword(RedisPassword.of(
                System.getenv().getOrDefault("REDIS_TEST_PASSWORD", "integration-test-only")));
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        deleteTestKeys();
        repository = new RedisMarketSnapshotRepository(
                redis, new ObjectMapper().findAndRegisterModules(), 60);
    }

    @AfterEach
    void tearDown() {
        deleteTestKeys();
        connectionFactory.destroy();
    }

    @Test
    void atomicallyReplacesHashWithTtlAndSafelyReleasesLock() {
        Instant fetchedAt = Instant.parse("2026-07-22T02:00:00Z");
        Quote quote = new Quote("SSE:600519", "贵州茅台", new BigDecimal("1450.50"),
                new BigDecimal("1440"), new BigDecimal("1442"), new BigDecimal("1460"),
                new BigDecimal("1438"), new BigDecimal("10.50"), new BigDecimal("0.73"),
                new BigDecimal("12345600"), "CONTINUOUS", "AKSHARE_EASTMONEY_SNAPSHOT",
                fetchedAt, fetchedAt, true, false, false);

        repository.replace(MarketDataConfig.SnapshotSource.EASTMONEY, List.of(quote));

        assertThat(repository.find(MarketDataConfig.SnapshotSource.EASTMONEY,
                List.of(InstrumentId.parse("SSE:600519")))).containsExactly(quote);
        assertThat(repository.metadata(MarketDataConfig.SnapshotSource.EASTMONEY))
                .get().extracting(RedisMarketSnapshotRepository.SnapshotMetadata::quoteCount)
                .isEqualTo(1);
        assertThat(redis.getExpire(PREFIX + "eastmoney")).isPositive();
        assertThat(redis.keys(PREFIX + "eastmoney:tmp:*")).isEmpty();

        String firstToken = repository.acquireRefreshLock(10).orElseThrow();
        assertThat(repository.acquireRefreshLock(10)).isEmpty();
        repository.releaseRefreshLock("not-the-owner");
        assertThat(repository.acquireRefreshLock(10)).isEmpty();
        repository.releaseRefreshLock(firstToken);
        assertThat(repository.acquireRefreshLock(10)).isPresent();
    }

    private void deleteTestKeys() {
        Set<String> keys = redis.keys(PREFIX + "*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
    }
}
