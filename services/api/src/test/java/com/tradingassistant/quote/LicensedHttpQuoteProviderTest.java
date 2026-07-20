package com.tradingassistant.quote;

import com.sun.net.httpserver.HttpServer;
import com.tradingassistant.config.AppProperties;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class LicensedHttpQuoteProviderTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void readsAkshareContractAndSendsSharedApiKey() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/snapshots", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-API-Key"))
                    .isEqualTo("test-shared-key");
            byte[] body = quoteJson().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        var http = new AppProperties.Quotes.HttpProvider(
                true, "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-shared-key", 10);
        var properties = new AppProperties(null, null,
                new AppProperties.Quotes(15, 2000, http));
        var provider = new LicensedHttpQuoteProvider(RestClient.builder(), properties);

        Quote quote = provider.snapshots(List.of(InstrumentId.parse("600519"))).get(0);

        assertThat(provider.priority()).isEqualTo(10);
        assertThat(quote.instrumentId()).isEqualTo("SSE:600519");
        assertThat(quote.source()).isEqualTo("AKSHARE");
        assertThat(quote.demo()).isFalse();
        assertThat(quote.delayed()).isTrue();
        assertThat(quote.last()).isEqualByComparingTo("1450.5");
    }

    private String quoteJson() {
        String timestamp = Instant.parse("2026-07-20T01:31:00Z").toString();
        return """
                [{
                  "instrumentId":"SSE:600519",
                  "name":"贵州茅台",
                  "last":1450.5,
                  "previousClose":1440,
                  "open":1441,
                  "high":1460,
                  "low":1430,
                  "change":10.5,
                  "changePercent":0.7292,
                  "volume":123456,
                  "marketPhase":"CONTINUOUS",
                  "source":"AKSHARE",
                  "sourceTimestamp":"%s",
                  "receivedAt":"%s",
                  "delayed":true,
                  "stale":false,
                  "demo":false
                }]
                """.formatted(timestamp, timestamp);
    }
}
