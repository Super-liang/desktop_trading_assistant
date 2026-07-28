package com.tradingassistant.performance;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/performance")
public class PerformanceController {
    private final PerformanceService performance;
    public PerformanceController(PerformanceService performance) { this.performance = performance; }

    @GetMapping
    PerformanceSummary current(@AuthenticationPrincipal Jwt jwt) {
        return performance.current(UUID.fromString(jwt.getSubject()));
    }
}
