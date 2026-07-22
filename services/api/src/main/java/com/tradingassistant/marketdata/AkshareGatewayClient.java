package com.tradingassistant.marketdata;

import com.tradingassistant.config.AppProperties;
import com.tradingassistant.quote.InstrumentId;
import com.tradingassistant.quote.Quote;
import com.tradingassistant.quote.QuoteProvider;
import java.time.Instant;
import java.time.Duration;
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
        this.statusClient = builder.clone().requestFactory(requestFactory(Duration.ofSeconds(5)))
                .baseUrl(config.baseUrl())
                .defaultHeader("X-API-Key", config.apiKey())
                .build();
    }

    public List<Quote> marketSnapshot(MarketDataConfig.SnapshotSource source) {
        Quote[] result = client.get().uri(uri -> uri.path("/v1/market/snapshot")
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

    public List<QuoteProvider.InstrumentSearchResult> search(String query) {
        List<QuoteProvider.InstrumentSearchResult> result = client.get()
                .uri(uri -> uri.path("/v1/instruments/search").queryParam("query", query).build())
                .retrieve().body(new ParameterizedTypeReference<>() {});
        return result == null ? List.of() : result;
    }

    public List<CatalogInstrument> catalog() {
        List<CatalogInstrument> result = client.get().uri("/v1/instruments/catalog")
                .retrieve().body(new ParameterizedTypeReference<>() {});
        return result == null ? List.of() : result;
    }

    public GatewayHealth health() {
        return statusClient.get().uri("/health").retrieve().body(GatewayHealth.class);
    }

    public List<SourceHealth> sourceStatus() {
        List<SourceHealth> result = statusClient.get().uri("/v1/sources/status").retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return result == null ? List.of() : result;
    }

    private record SingleQuoteRequest(String source, List<String> symbols) {}

    private static SimpleClientHttpRequestFactory requestFactory(Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    public record GatewayHealth(String status, String source) {}
    public record SourceHealth(String source, String status, Instant lastAttemptAt,
            Instant lastSuccessAt, Long latencyMillis, String errorType) {}
    public record CatalogInstrument(String instrumentId, String code, String name,
            InstrumentId.Exchange exchange, InstrumentId.AssetType assetType) {}
}
