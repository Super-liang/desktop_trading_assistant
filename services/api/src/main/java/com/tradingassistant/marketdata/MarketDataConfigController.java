package com.tradingassistant.marketdata;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MarketDataConfigController {
    private final MarketDataConfigService service;

    public MarketDataConfigController(MarketDataConfigService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/market-data/config")
    public ConfigView current() {
        return ConfigView.from(service.current());
    }

    @PutMapping("/api/v1/admin/market-data/config")
    public ConfigView update(@AuthenticationPrincipal Jwt jwt,
            @RequestBody MarketDataConfigService.UpdateRequest request) {
        try {
            return ConfigView.from(service.update(UUID.fromString(jwt.getSubject()), request));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    public record ProviderDescriptor(String id, String name, List<String> modes) {}
    public record ConfigView(MarketDataConfig.Provider provider, MarketDataConfig.Mode mode,
            MarketDataConfig.SnapshotSource snapshotSource,
            MarketDataConfig.SingleSource singleSource, int refreshSeconds,
            Instant updatedAt, List<ProviderDescriptor> providers) {
        static ConfigView from(MarketDataConfig config) {
            return new ConfigView(config.getProvider(), config.getMode(),
                    config.getSnapshotSource(), config.getSingleSource(),
                    config.getRefreshSeconds(), config.getUpdatedAt(),
                    List.of(new ProviderDescriptor("AKSHARE", "AKShare",
                            List.of("MARKET_SNAPSHOT", "SINGLE_STOCK"))));
        }
    }
}
