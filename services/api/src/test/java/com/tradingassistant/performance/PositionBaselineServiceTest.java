package com.tradingassistant.performance;

import com.tradingassistant.market.*;
import com.tradingassistant.portfolio.PortfolioItem;
import com.tradingassistant.portfolio.PortfolioRepository;
import com.tradingassistant.quote.InstrumentId;
import com.tradingassistant.quote.Quote;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PositionBaselineServiceTest {
    @Mock PortfolioRepository positions;
    @Mock PositionDailyBaselineRepository baselines;
    @Mock MarketSessionRepository sessions;

    @Test
    void capturesOpeningQuantityAndPriceOnceAndReusesItDuringLunch() {
        Instant now = Instant.now();
        Instant open = now.plusSeconds(60);
        Instant captureAt = now.plusSeconds(120);
        LocalDate date = captureAt.atZone(Market.A_SHARE.timezone()).toLocalDate();
        PortfolioItem position = position();
        when(positions.findAllByMarketOrderByCreatedAtAsc(Market.A_SHARE))
                .thenReturn(List.of(position));
        when(sessions.findByMarketAndTradingDate(Market.A_SHARE, date))
                .thenReturn(Optional.of(session(date, open, captureAt.plusSeconds(10_000))));
        Map<PositionDailyBaselineId, PositionDailyBaseline> stored = new HashMap<>();
        when(baselines.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(stored.get(invocation.getArgument(0))));
        when(baselines.save(any())).thenAnswer(invocation -> {
            PositionDailyBaseline value = invocation.getArgument(0);
            stored.put(new PositionDailyBaselineId(value.getPositionId(), value.getTradingDate()), value);
            return value;
        });
        PositionBaselineService service = new PositionBaselineService(positions, baselines, sessions);

        service.capture(Market.A_SHARE, List.of(quote(new BigDecimal("10"))), captureAt);
        service.capture(Market.A_SHARE, List.of(quote(new BigDecimal("11"))),
                captureAt.plusSeconds(3600));

        PositionDailyBaseline result = stored.values().iterator().next();
        assertThat(result.getOpeningQuantity()).isEqualByComparingTo("100");
        assertThat(result.getOpeningPrice()).isEqualByComparingTo("10");
        assertThat(result.getStatus()).isEqualTo(PositionDailyBaseline.Status.COMPLETE);
    }

    @Test
    void missingOpenCanRecoverButPositionCreatedAfterOpenCannot() {
        Instant now = Instant.now();
        LocalDate date = now.atZone(Market.A_SHARE.timezone()).toLocalDate();
        PortfolioItem position = position();
        when(positions.findAllByMarketOrderByCreatedAtAsc(Market.A_SHARE))
                .thenReturn(List.of(position));
        Map<PositionDailyBaselineId, PositionDailyBaseline> stored = new HashMap<>();
        when(baselines.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(stored.get(invocation.getArgument(0))));
        when(baselines.save(any())).thenAnswer(invocation -> {
            PositionDailyBaseline value = invocation.getArgument(0);
            stored.put(new PositionDailyBaselineId(value.getPositionId(), value.getTradingDate()), value);
            return value;
        });
        PositionBaselineService service = new PositionBaselineService(positions, baselines, sessions);

        // 会话开盘晚于持仓创建：缺失今开先标记，下一次有效行情可恢复。
        Instant futureOpen = now.plusSeconds(30);
        Instant capture = now.plusSeconds(60);
        when(sessions.findByMarketAndTradingDate(Market.A_SHARE, date))
                .thenReturn(Optional.of(session(date, futureOpen, capture.plusSeconds(3600))));
        service.capture(Market.A_SHARE, List.of(quote(BigDecimal.ZERO)), capture);
        assertThat(stored.values().iterator().next().getStatusReason())
                .isEqualTo(PositionDailyBaseline.Reason.MISSING_OPEN);
        service.capture(Market.A_SHARE, List.of(quote(BigDecimal.TEN)), capture.plusSeconds(1));
        assertThat(stored.values().iterator().next().getStatus())
                .isEqualTo(PositionDailyBaseline.Status.COMPLETE);

        stored.clear();
        // 会话已开盘后才创建的持仓，当日不能建立标准日收益基线。
        when(sessions.findByMarketAndTradingDate(Market.A_SHARE, date))
                .thenReturn(Optional.of(session(date, now.minusSeconds(3600), now.plusSeconds(3600))));
        service.capture(Market.A_SHARE, List.of(quote(BigDecimal.TEN)), now.plusSeconds(1));
        assertThat(stored.values().iterator().next().getStatusReason())
                .isEqualTo(PositionDailyBaseline.Reason.MUTATED_AFTER_OPEN);

        // 下一交易日该持仓已在开盘前存在，可重新建立正常基线。
        Instant nextCapture = now.plus(Duration.ofDays(1)).plusSeconds(120);
        LocalDate nextDate = nextCapture.atZone(Market.A_SHARE.timezone()).toLocalDate();
        when(sessions.findByMarketAndTradingDate(Market.A_SHARE, nextDate))
                .thenReturn(Optional.of(session(nextDate, nextCapture.minusSeconds(60),
                        nextCapture.plusSeconds(3600))));
        service.capture(Market.A_SHARE, List.of(quote(BigDecimal.TEN)), nextCapture);
        assertThat(stored.get(new PositionDailyBaselineId(position.getId(), nextDate)).getStatus())
                .isEqualTo(PositionDailyBaseline.Status.COMPLETE);
    }

    private PortfolioItem position() {
        return new PortfolioItem(UUID.randomUUID(), InstrumentId.parse("SSE:600519"), "贵州茅台",
                new BigDecimal("100"), new BigDecimal("8"), 0);
    }

    private MarketSession session(LocalDate date, Instant open, Instant close) {
        return new MarketSession(Market.A_SHARE, date, "Asia/Shanghai", open,
                open.plusSeconds(7200), open.plusSeconds(9000), close, false,
                "TEST", false, Instant.now());
    }

    private Quote quote(BigDecimal open) {
        Instant now = Instant.now();
        return new Quote("SSE:600519", "贵州茅台", BigDecimal.TEN, BigDecimal.TEN,
                open, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ONE, "OPEN", "AKSHARE_EASTMONEY_SNAPSHOT", now, now,
                true, false, false);
    }
}
