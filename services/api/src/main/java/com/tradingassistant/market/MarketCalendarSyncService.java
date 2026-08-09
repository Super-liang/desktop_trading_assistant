package com.tradingassistant.market;

import com.tradingassistant.marketdata.AkshareGatewayClient;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketCalendarSyncService {
    private static final Logger log = LoggerFactory.getLogger(MarketCalendarSyncService.class);
    private final MarketSessionRepository sessions;
    private final ObjectProvider<AkshareGatewayClient> gatewayProvider;

    public MarketCalendarSyncService(MarketSessionRepository sessions,
            ObjectProvider<AkshareGatewayClient> gatewayProvider) {
        this.sessions = sessions;
        this.gatewayProvider = gatewayProvider;
    }

    @Scheduled(cron = "0 30 2 * * *", zone = "Asia/Shanghai")
    @Transactional
    public void synchronizeRollingWindow() {
        AkshareGatewayClient gateway = gatewayProvider.getIfAvailable();
        if (gateway == null) {
            log.warn("跳过交易日历同步：AKShare 网关未启用");
            return;
        }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        try {
            AkshareGatewayClient.CalendarCrossCheck check = gateway.aShareCalendarCheck(
                    today.minusDays(30), today);
            if (check != null && "MISMATCH".equals(check.status())) {
                log.warn("A 股交易日历来源存在差异：exchangeCalendarsOnly={},akshareOnly={}",
                        check.onlyExchangeCalendars().size(), check.onlyAkshare().size());
            }
        } catch (RuntimeException exception) {
            // 交叉来源失效不阻止主日历写入，但会在网关来源健康中标记为 DOWN。
            log.warn("A 股交易日历交叉检查不可用，继续同步主日历：error={}",
                    exception.getClass().getSimpleName());
        }
        Instant syncedAt = Instant.now();
        int updated = 0;
        for (AkshareGatewayClient.CalendarSession row : gateway.calendarSessions(
                today.minusDays(30), today.plusDays(400))) {
            Market market = Market.valueOf(row.market());
            MarketSession incoming = new MarketSession(market, row.tradingDate(), row.timezone(),
                    row.openAt(), row.breakStartAt(), row.breakEndAt(), row.closeAt(),
                    row.earlyClose(), row.source(), false, syncedAt);
            MarketSession target = sessions.findById(new MarketSessionId(market, row.tradingDate()))
                    .orElse(incoming);
            target.refreshFrom(incoming);
            sessions.save(target);
            updated++;
        }
        log.info("交易日历同步完成：sessionCount={}", updated);
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void synchronizeAfterStartup() {
        try {
            synchronizeRollingWindow();
        } catch (RuntimeException exception) {
            log.warn("应用启动后的交易日历同步失败，将按计划任务重试：error={}",
                    exception.getClass().getSimpleName());
        }
    }
}
