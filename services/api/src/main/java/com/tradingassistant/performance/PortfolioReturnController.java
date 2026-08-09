package com.tradingassistant.performance;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/returns")
public class PortfolioReturnController {
    private final PortfolioReturnService returns;

    public PortfolioReturnController(PortfolioReturnService returns) {
        this.returns = returns;
    }

    @GetMapping
    PortfolioReturns current(@AuthenticationPrincipal Jwt jwt) {
        return returns.current(UUID.fromString(jwt.getSubject()));
    }
}
