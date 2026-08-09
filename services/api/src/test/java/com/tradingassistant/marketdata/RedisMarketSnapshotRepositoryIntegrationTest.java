package com.tradingassistant.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingassistant.quote.InstrumentId;
import com.tradingassistant.quote.Quote;
import com.tradingassistant.market.Market;
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
    private static final String PREFIX = "trading:quotes:akshare:a_share:snapshot:";
    private static final String ROOT_PREFIX = "trading:quotes:akshare:";
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
                redis, new ObjectMapper().findAndRegisterModules());
    }

    @AfterEach
    void tearDown() {
        deleteTestKeys();
        connectionFactory.destroy();
    }

    @Test
    void incrementallyUpdatesPermanentSnapshotAndSafelyReleasesLock() {
        Instant fetchedAt = Instant.parse("2026-07-22T02:00:00Z");
        Quote quote = new Quote("SSE:600519", "贵州茅台", new BigDecimal("1450.50"),
                new BigDecimal("1440"), new BigDecimal("1442"), new BigDecimal("1460"),
                new BigDecimal("1438"), new BigDecimal("10.50"), new BigDecimal("0.73"),
                new BigDecimal("12345600"), "CONTINUOUS", "AKSHARE_EASTMONEY_SNAPSHOT",
                fetchedAt, fetchedAt, true, false, false);

        repository.replace(MarketDataConfig.SnapshotSource.EASTMONEY, List.of(quote));

        assertThat(repository.find(MarketDataConfig.SnapshotSource.EASTMONEY,
                List.of(InstrumentId.parse("SSE:600519")))).containsExactly(quote);
        redis.expire(PREFIX + "eastmoney", Duration.ofSeconds(60));
        assertThat(repository.metadata(MarketDataConfig.SnapshotSource.EASTMONEY))
                .get().extracting(RedisMarketSnapshotRepository.SnapshotMetadata::quoteCount)
                .isEqualTo(1);
        assertThat(redis.getExpire(PREFIX + "eastmoney")).isEqualTo(-1);
        assertThat(redis.hasKey(PREFIX + "eastmoney:meta")).isFalse();

        Quote second = new Quote("SZSE:000001", "平安银行", new BigDecimal("11.20"),
                new BigDecimal("11.10"), new BigDecimal("11.12"), new BigDecimal("11.30"),
                new BigDecimal("11.00"), new BigDecimal("0.10"), new BigDecimal("0.90"),
                new BigDecimal("9876500"), "CONTINUOUS", "AKSHARE_EASTMONEY_SNAPSHOT",
                fetchedAt, fetchedAt, true, false, false);
        repository.replace(MarketDataConfig.SnapshotSource.EASTMONEY, List.of(second));
        assertThat(repository.find(MarketDataConfig.SnapshotSource.EASTMONEY, List.of(
                InstrumentId.parse("SSE:600519"), InstrumentId.parse("SZSE:000001"))))
                .containsExactly(quote, second);

        String firstToken = repository.acquireRefreshLock(
                MarketDataConfig.SnapshotSource.EASTMONEY, 30).orElseThrow();
        assertThat(repository.acquireRefreshLock(MarketDataConfig.SnapshotSource.EASTMONEY, 30))
                .isEmpty();
        assertThat(repository.acquireRefreshLock(MarketDataConfig.SnapshotSource.SINA, 30))
                .isPresent();
        repository.releaseRefreshLock(MarketDataConfig.SnapshotSource.EASTMONEY,
                "not-the-owner");
        assertThat(repository.acquireRefreshLock(MarketDataConfig.SnapshotSource.EASTMONEY, 30))
                .isEmpty();
        repository.releaseRefreshLock(MarketDataConfig.SnapshotSource.EASTMONEY, firstToken);
        assertThat(repository.acquireRefreshLock(MarketDataConfig.SnapshotSource.EASTMONEY, 30))
                .isPresent();
    }

    @Test
    void startupMigrationRecoversMetadataFromQuotesAndRemovesLegacyTtl() {
        Instant fetchedAt = Instant.parse("2026-07-22T02:00:00Z");
        Quote quote = new Quote("SSE:600519", "贵州茅台", new BigDecimal("1450.50"),
                new BigDecimal("1440"), new BigDecimal("1442"), new BigDecimal("1460"),
                new BigDecimal("1438"), new BigDecimal("10.50"), new BigDecimal("0.73"),
                new BigDecimal("12345600"), "CONTINUOUS", "AKSHARE_SINA_SNAPSHOT",
                fetchedAt, fetchedAt, true, false, false);
        repository.replace(MarketDataConfig.SnapshotSource.SINA, List.of(quote));
        redis.opsForHash().delete(PREFIX + "sina", "__metadata__");
        redis.expire(PREFIX + "sina", Duration.ofSeconds(60));

        repository.migrateLegacySnapshots();

        assertThat(repository.metadata(MarketDataConfig.SnapshotSource.SINA))
                .get().extracting(RedisMarketSnapshotRepository.SnapshotMetadata::fetchedAt)
                .isEqualTo(fetchedAt);
        assertThat(redis.getExpire(PREFIX + "sina")).isEqualTo(-1);
        assertThat(repository.find(MarketDataConfig.SnapshotSource.SINA,
                List.of(InstrumentId.parse("SSE:600519")))).containsExactly(quote);
    }

    @Test
    void rejectsEmptyRefreshWithoutChangingLastSnapshot() {
        Quote quote = new Quote("SSE:600519", "贵州茅台", new BigDecimal("1450.50"),
                new BigDecimal("1440"), new BigDecimal("1442"), new BigDecimal("1460"),
                new BigDecimal("1438"), new BigDecimal("10.50"), new BigDecimal("0.73"),
                new BigDecimal("12345600"), "CONTINUOUS", "AKSHARE_SINA_SNAPSHOT",
                Instant.parse("2026-07-22T02:00:00Z"), Instant.parse("2026-07-22T02:00:00Z"),
                true, false, false);
        repository.replace(MarketDataConfig.SnapshotSource.SINA, List.of(quote));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> repository.replace(MarketDataConfig.SnapshotSource.SINA, List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(repository.find(MarketDataConfig.SnapshotSource.SINA,
                List.of(InstrumentId.parse("SSE:600519")))).containsExactly(quote);
    }

    @Test
    void marketAndSourceKeysNeverPolluteEachOtherAndHaveNoTtl() {
        Instant at = Instant.parse("2026-07-22T14:00:00Z");
        Quote hk = quote("HKEX:00700", "腾讯控股", at);
        Quote us = quote("NASDAQ:AAPL", "Apple", at);

        repository.replace(Market.HK_STOCK, MarketDataConfig.SnapshotSource.EASTMONEY,
                List.of(hk));
        repository.replace(Market.US_STOCK, MarketDataConfig.SnapshotSource.EASTMONEY,
                List.of(us));

        assertThat(repository.find(Market.HK_STOCK,
                MarketDataConfig.SnapshotSource.EASTMONEY, List.of("HKEX:00700")))
                .containsExactly(hk);
        assertThat(repository.find(Market.US_STOCK,
                MarketDataConfig.SnapshotSource.EASTMONEY, List.of("NASDAQ:AAPL")))
                .containsExactly(us);
        assertThat(repository.find(Market.HK_STOCK,
                MarketDataConfig.SnapshotSource.EASTMONEY, List.of("NASDAQ:AAPL")))
                .isEmpty();
        assertThat(redis.getExpire("trading:quotes:akshare:hk_stock:snapshot:eastmoney"))
                .isEqualTo(-1);
    }

    private Quote quote(String instrumentId, String name, Instant at) {
        return new Quote(instrumentId, name, BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, "OPEN", "AKSHARE", at, at,
                true, false, false);
    }

    private void deleteTestKeys() {
        Set<String> keys = redis.keys(ROOT_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
    }
}
