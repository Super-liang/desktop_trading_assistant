package com.tradingassistant.performance;

import com.tradingassistant.market.Market;
import com.tradingassistant.market.MarketSession;
import com.tradingassistant.market.MarketSessionRepository;
import com.tradingassistant.portfolio.PortfolioItem;
import com.tradingassistant.portfolio.PortfolioRepository;
import com.tradingassistant.quote.Quote;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PositionBaselineService {
    private static final Logger log = LoggerFactory.getLogger(PositionBaselineService.class);
    private final PortfolioRepository positions;
    private final PositionDailyBaselineRepository baselines;
    private final MarketSessionRepository sessions;

    public PositionBaselineService(PortfolioRepository positions,
            PositionDailyBaselineRepository baselines, MarketSessionRepository sessions) {
        this.positions = positions;
        this.baselines = baselines;
        this.sessions = sessions;
    }

    @Transactional
    public void capture(Market market, List<Quote> quotes, Instant capturedAt) {
        if (market == Market.PUBLIC_FUND) return;
        LocalDate tradingDate = capturedAt.atZone(market.timezone()).toLocalDate();
        MarketSession session = sessions.findByMarketAndTradingDate(market, tradingDate)
                .orElse(null);
        if (session == null || capturedAt.isBefore(session.getOpenAt())
                || !capturedAt.isBefore(session.getCloseAt())) return;
        Map<String, Quote> quoteByInstrument = quotes.stream().collect(Collectors.toMap(
                Quote::instrumentId, value -> value, (first, ignored) -> first));
        int completed = 0;
        int unavailable = 0;
        for (PortfolioItem position : positions.findAllByMarketOrderByCreatedAtAsc(market)) {
            PositionDailyBaselineId id = new PositionDailyBaselineId(
                    position.getId(), tradingDate);
            PositionDailyBaseline baseline = baselines.findById(id).orElseGet(() ->
                    new PositionDailyBaseline(position.getId(), position.getUserId(), market,
                            tradingDate, position.getCurrency()));
            if (baseline.getStatusReason() == PositionDailyBaseline.Reason.MUTATED_AFTER_OPEN) {
                continue;
            }
            if (!position.getUpdatedAt().isBefore(session.getOpenAt())) {
                baseline.unavailable(PositionDailyBaseline.Reason.MUTATED_AFTER_OPEN,
                        null, capturedAt);
                baselines.save(baseline);
                unavailable++;
                continue;
            }
            if (baseline.getStatus() == PositionDailyBaseline.Status.COMPLETE) continue;
            Quote quote = quoteByInstrument.get(position.canonical());
            BigDecimal openingPrice = quote == null ? null : quote.open();
            if (openingPrice == null || openingPrice.signum() <= 0) {
                baseline.unavailable(PositionDailyBaseline.Reason.MISSING_OPEN,
                        quote == null ? null : quote.source(), capturedAt);
                unavailable++;
            } else {
                baseline.complete(position.getQuantity(), openingPrice, quote.source(), capturedAt);
                completed++;
            }
            baselines.save(baseline);
        }
        log.info("持仓开盘基线采集：market={},tradingDate={},complete={},unavailable={}",
                market, tradingDate, completed, unavailable);
    }
}
