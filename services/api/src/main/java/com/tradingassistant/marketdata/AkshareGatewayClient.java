package com.tradingassistant.marketdata;

import com.tradingassistant.config.AppProperties;
import com.tradingassistant.quote.InstrumentId;
import com.tradingassistant.market.AssetType;
import com.tradingassistant.market.Currency;
import com.tradingassistant.market.Exchange;
import com.tradingassistant.market.Market;
import com.tradingassistant.quote.Quote;
import com.tradingassistant.quote.QuoteProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "app.quotes.http.enabled", havingValue = "true")
public class AkshareGatewayClient {
    private final RestClient client;
    private final RestClient catalogClient;
    private final RestClient statusClient;

    public AkshareGatewayClient(RestClient.Builder builder, AppProperties properties) {
        AppProperties.Quotes.HttpProvider config = properties.quotes().http();
        if (config.baseUrl() == null || config.baseUrl().isBlank()
                || config.apiKey() == null || config.apiKey().isBlank()) {
            throw new IllegalStateException("启用 AKShare 时必须配置网关地址和共享密钥");
        }
        this.client = builder.clone().requestFactory(requestFactory(Duration.ofSeconds(60)))
                .baseUrl(config.baseUrl())
                .defaultHeader("X-API-Key", config.apiKey())
                .build();
        // 美股新浪降级目录需要分页获取，目录同步使用独立总超时，避免影响行情请求的 60 秒边界。
        this.catalogClient = builder.clone().requestFactory(requestFactory(Duration.ofMinutes(16)))
                .baseUrl(config.baseUrl())
                .defaultHeader("X-API-Key", config.apiKey())
                .build();
        this.statusClient = builder.clone().requestFactory(requestFactory(Duration.ofSeconds(5)))
                .baseUrl(config.baseUrl())
                .defaultHeader("X-API-Key", config.apiKey())
                .build();
    }

    public List<Quote> marketSnapshot(MarketDataConfig.SnapshotSource source) {
        return marketSnapshot(Market.A_SHARE, source);
    }

    public List<Quote> marketSnapshot(Market market, MarketDataConfig.SnapshotSource source) {
        Quote[] result = client.get().uri(uri -> uri.path("/v1/market/snapshot")
                        .queryParam("market", market.name())
                        .queryParam("source", source.name()).build())
                .retrieve().body(Quote[].class);
        return result == null ? List.of() : Arrays.asList(result);
    }

    public List<Quote> singleQuotes(MarketDataConfig.SingleSource source,
            List<InstrumentId> instruments) {
        SingleQuoteRequest request = new SingleQuoteRequest(source.name(),
                instruments.stream().map(InstrumentId::canonical).toList());
        Quote[] result = client.post().uri("/v1/quotes/single").body(request)
                .retrieve().body(Quote[].class);
        return result == null ? List.of() : Arrays.asList(result);
    }

    public List<Quote> usPositionQuotes(List<String> instrumentIds) {
        UsPositionQuoteRequest request = new UsPositionQuoteRequest(instrumentIds);
        Quote[] result = client.post().uri("/v1/quotes/us-positions").body(request)
                .retrieve().body(Quote[].class);
        return result == null ? List.of() : Arrays.asList(result);
    }

    public List<QuoteProvider.InstrumentSearchResult> search(String query) {
        List<QuoteProvider.InstrumentSearchResult> result = client.get()
                .uri(uri -> uri.path("/v1/instruments/search").queryParam("query", query).build())
                .retrieve().body(new ParameterizedTypeReference<>() {});
        return result == null ? List.of() : result;
    }

    public List<CatalogInstrument> catalog(Market market) {
        List<CatalogInstrument> result = catalogClient.get().uri(uri -> uri
                        .path("/v1/instruments/catalog")
                        .queryParam("market", market.name()).build())
                .retrieve().body(new ParameterizedTypeReference<>() {});
        return result == null ? List.of() : result;
    }

    public List<CatalogInstrument> catalog() { return catalog(Market.A_SHARE); }

    public GatewayHealth health() {
        return statusClient.get().uri("/health").retrieve().body(GatewayHealth.class);
    }

    public List<SourceHealth> sourceStatus() {
        List<SourceHealth> result = statusClient.get().uri("/v1/sources/status").retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return result == null ? List.of() : result;
    }

    public List<CalendarSession> calendarSessions(LocalDate start, LocalDate end) {
        List<CalendarSession> result = client.get().uri(uri -> uri.path("/v1/calendars/sessions")
                        .queryParam("start", start).queryParam("end", end).build())
                .retrieve().body(new ParameterizedTypeReference<>() {});
        return result == null ? List.of() : result;
    }

    public CalendarCrossCheck aShareCalendarCheck(LocalDate start, LocalDate end) {
        return client.get().uri(uri -> uri.path("/v1/calendars/a-share-check")
                        .queryParam("start", start).queryParam("end", end).build())
                .retrieve().body(CalendarCrossCheck.class);
    }

    public List<FundNav> allFundUnitNav() {
        List<FundNav> result = client.get().uri("/v1/funds/unit-nav")
                .retrieve().body(new ParameterizedTypeReference<>() {});
        return result == null ? List.of() : result;
    }

    public List<MarketIndexQuote> indexOverview(MarketDataConfig.SnapshotSource source) {
        List<MarketIndexQuote> result = client.get().uri(uri -> uri.path("/v1/market/indices")
                        .queryParam("source", source.name()).build())
                .retrieve().body(new ParameterizedTypeReference<>() {});
        return result == null ? List.of() : result;
    }

    private record SingleQuoteRequest(String source, List<String> symbols) {}
    private record UsPositionQuoteRequest(List<String> symbols) {}

    private static SimpleClientHttpRequestFactory requestFactory(Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    public record GatewayHealth(String status, String source) {}
    public record SourceHealth(String source, String status, Instant lastAttemptAt,
            Instant lastSuccessAt, Long latencyMillis, String errorType) {}
    public record CatalogInstrument(String instrumentId, String code, String name, Market market,
            Exchange exchange, Currency currency, AssetType assetType, String providerSymbol) {}
    public record CalendarSession(
            String market,
            @com.fasterxml.jackson.annotation.JsonProperty("trading_date") LocalDate tradingDate,
            String timezone,
            @com.fasterxml.jackson.annotation.JsonProperty("open_at") Instant openAt,
            @com.fasterxml.jackson.annotation.JsonProperty("break_start_at") Instant breakStartAt,
            @com.fasterxml.jackson.annotation.JsonProperty("break_end_at") Instant breakEndAt,
            @com.fasterxml.jackson.annotation.JsonProperty("close_at") Instant closeAt,
            @com.fasterxml.jackson.annotation.JsonProperty("early_close") boolean earlyClose,
            String source) {}
    public record CalendarCrossCheck(
            List<LocalDate> onlyExchangeCalendars,
            List<LocalDate> onlyAkshare,
            String status,
            String source,
            Instant checkedAt) {}
    public record FundNav(
            String instrumentId,
            String code,
            String name,
            BigDecimal unitNav,
            LocalDate navDate,
            BigDecimal previousUnitNav,
            LocalDate previousNavDate,
            String source,
            Instant sourceUpdatedAt) {}
}
