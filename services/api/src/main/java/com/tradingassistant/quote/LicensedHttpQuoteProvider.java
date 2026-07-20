package com.tradingassistant.quote;

import com.tradingassistant.config.AppProperties;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 获授权行情桥接器：供应商 SDK、专线或聚合服务可统一封装成此 HTTP 契约。
 * API Key 只进入请求头且不写日志；异常由 Registry 在单次调用内自动降级。
 */
@Component
@ConditionalOnProperty(name = "app.quotes.http.enabled", havingValue = "true")
public class LicensedHttpQuoteProvider implements QuoteProvider {
    private final RestClient client;
    private final int priority;

    public LicensedHttpQuoteProvider(RestClient.Builder builder, AppProperties properties) {
        AppProperties.Quotes.HttpProvider config = properties.quotes().http();
        if (config.baseUrl() == null || config.baseUrl().isBlank()
                || config.apiKey() == null || config.apiKey().isBlank()) {
            throw new IllegalStateException("启用授权行情源时必须配置 QUOTE_HTTP_BASE_URL 和 QUOTE_HTTP_API_KEY");
        }
        this.priority = config.priority();
        this.client = builder.baseUrl(config.baseUrl())
                .defaultHeader("X-API-Key", config.apiKey())
                .build();
    }

    @Override public String id() { return "LICENSED_HTTP"; }
    @Override public int priority() { return priority; }
    @Override public boolean healthy() { return true; }
    @Override public boolean demo() { return false; }
    @Override public Set<InstrumentId.Exchange> exchanges() {
        return Set.of(InstrumentId.Exchange.SSE, InstrumentId.Exchange.SZSE,
                InstrumentId.Exchange.BSE);
    }

    @Override
    public List<InstrumentSearchResult> search(String query) {
        List<InstrumentSearchResult> result = client.get()
                .uri(uri -> uri.path("/v1/search").queryParam("query", query).build())
                .retrieve().body(new ParameterizedTypeReference<>() {});
        return result == null ? List.of() : result;
    }

    @Override
    public List<Quote> snapshots(List<InstrumentId> instruments) {
        String symbols = instruments.stream().map(InstrumentId::canonical)
                .collect(Collectors.joining(","));
        Quote[] result = client.get()
                .uri(uri -> uri.path("/v1/snapshots").queryParam("symbols", symbols).build())
                .retrieve().body(Quote[].class);
        return result == null ? List.of() : Arrays.asList(result);
    }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(exchanges(), true, true, false, 50,
                "LICENSED_PC_DISPLAY_AND_DISTRIBUTION_REQUIRED");
    }
}
