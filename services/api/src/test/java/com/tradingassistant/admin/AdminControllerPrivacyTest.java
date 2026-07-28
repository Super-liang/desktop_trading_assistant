package com.tradingassistant.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingassistant.audit.*;
import com.tradingassistant.auth.*;
import com.tradingassistant.performance.*;
import com.tradingassistant.portfolio.*;
import com.tradingassistant.quote.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminControllerPrivacyTest {
    @Mock UserRepository users;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock AdminAuditRepository adminAudits;
    @Mock UserOperationAuditRepository userAudits;
    @Mock PortfolioRepository portfolios;
    @Mock QuoteProviderRegistry quotes;
    @Mock PerformanceService performance;

    @Test
    void overviewAndHoldingsNeverSerializePrivatePositionValues() throws Exception {
        User user = new User("private@example.com", "隐私用户", "hash", User.Role.USER);
        PortfolioItem item = new PortfolioItem(user.getId(), InstrumentId.parse("SSE:600519"),
                "贵州茅台", new BigDecimal("1000"), new BigDecimal("1500"), 0);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(portfolios.countByUserId(user.getId())).thenReturn(1L);
        when(portfolios.findAllByUserIdOrderBySortOrderAscCreatedAtAsc(user.getId()))
                .thenReturn(List.of(item));
        when(performance.current(user.getId())).thenReturn(new PerformanceSummary(
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, null,
                LocalDate.now(), Instant.now(), PerformanceStatus.ACCUMULATING, 0,
                PerformanceSummary.REFERENCE_NOTICE));
        when(quotes.snapshots(anyList())).thenReturn(List.of());
        AdminController controller = controller();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        String overview = mapper.writeValueAsString(controller.overview(user.getId()));
        String holdings = mapper.writeValueAsString(controller.holdings(user.getId()));

        assertThat(overview).contains("holdingCount", "performance", "lastLoginStatus")
                .doesNotContain("quantity", "costPrice", "totalMarketValue");
        assertThat(holdings).contains("content", "SSE:600519", "quoteAvailable")
                .doesNotContain("quantity", "costPrice", "marketValue", "profit");
    }

    @Test
    void auditQueryIsAlwaysScopedToPathUserAndFilters() {
        User user = new User("audit@example.com", "审计用户", "hash", User.Role.USER);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(userAudits.search(eq(user.getId()),
                eq(UserOperationAudit.Action.PORTFOLIO_DELETED), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-02-01T00:00:00Z");

        controller().userAudits(user.getId(), UserOperationAudit.Action.PORTFOLIO_DELETED,
                from, to, 0, 20);

        verify(userAudits).search(eq(user.getId()),
                eq(UserOperationAudit.Action.PORTFOLIO_DELETED), eq(from), eq(to), any());
    }

    private AdminController controller() {
        return new AdminController(users, refreshTokens, adminAudits, userAudits,
                portfolios, quotes, performance);
    }
}
