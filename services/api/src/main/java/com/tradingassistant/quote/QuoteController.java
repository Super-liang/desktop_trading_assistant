package com.tradingassistant.quote;

import com.tradingassistant.config.AppProperties;
import com.tradingassistant.marketdata.MarketDataConfig;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/quotes")
public class QuoteController {
    private final QuoteProviderRegistry registry;
    private final AppProperties properties;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "quote-sse");
        thread.setDaemon(true);
        return thread;
    });

    public QuoteController(QuoteProviderRegistry registry, AppProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping("/search")
    List<QuoteProvider.InstrumentSearchResult> search(@RequestParam(defaultValue = "") String query) {
        return registry.search(query);
    }

    @GetMapping("/snapshots")
    List<Quote> snapshots(@RequestParam List<String> symbols,
            @RequestParam(required = false) MarketDataConfig.Mode mode,
            @RequestParam(required = false) MarketDataConfig.SnapshotSource snapshotSource,
            @RequestParam(required = false) MarketDataConfig.SingleSource singleSource) {
        return registry.snapshots(parse(symbols),
                new QuoteRequestOptions(mode, snapshotSource, singleSource));
    }

    @GetMapping("/providers")
    List<QuoteProviderRegistry.ProviderStatus> providers() {
        return registry.status();
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@RequestParam List<String> symbols,
            @RequestParam(required = false) MarketDataConfig.Mode mode,
            @RequestParam(required = false) MarketDataConfig.SnapshotSource snapshotSource,
            @RequestParam(required = false) MarketDataConfig.SingleSource singleSource) {
        List<InstrumentId> instruments = parse(symbols);
        SseEmitter emitter = new SseEmitter(0L);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("quotes")
                        .data(registry.snapshots(instruments,
                                new QuoteRequestOptions(mode, snapshotSource, singleSource))));
            } catch (IOException | QuoteUnavailableException exception) {
                emitter.completeWithError(exception);
            }
        }, 0, Math.max(500, properties.quotes().tickMillis()), TimeUnit.MILLISECONDS);
        emitter.onCompletion(() -> future.cancel(false));
        emitter.onTimeout(() -> future.cancel(false));
        emitter.onError(error -> future.cancel(false));
        return emitter;
    }

    private List<InstrumentId> parse(List<String> symbols) {
        if (symbols == null || symbols.isEmpty() || symbols.size() > 50) {
            throw new IllegalArgumentException("每次必须请求 1 到 50 个证券");
        }
        return symbols.stream().distinct().map(InstrumentId::parse).toList();
    }
}
