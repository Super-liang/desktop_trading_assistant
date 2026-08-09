package com.tradingassistant.market;

import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/markets")
public class MarketStatusController {
    private final MarketClock marketClock;

    public MarketStatusController(MarketClock marketClock) {
        this.marketClock = marketClock;
    }

    @GetMapping("/status")
    public List<MarketStatus> status() {
        return Arrays.stream(Market.values()).map(marketClock::status).toList();
    }
}
