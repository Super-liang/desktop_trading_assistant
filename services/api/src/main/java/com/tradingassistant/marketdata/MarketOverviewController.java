package com.tradingassistant.marketdata;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/market-overview")
public class MarketOverviewController {
    private final RedisIndexOverviewRepository repository;

    public MarketOverviewController(RedisIndexOverviewRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/a-share")
    public List<MarketIndexQuote> aShare(
            @RequestParam(defaultValue = "SINA") MarketDataConfig.SnapshotSource source) {
        // 参数保留兼容旧客户端，但大盘行情与全市场快照统一读取新浪。
        List<MarketIndexQuote> result = repository.find(MarketDataConfig.SnapshotSource.SINA);
        if (result.isEmpty()) throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "指数行情缓存尚未就绪");
        return result;
    }
}
