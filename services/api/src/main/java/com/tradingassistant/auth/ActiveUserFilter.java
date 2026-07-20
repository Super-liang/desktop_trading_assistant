package com.tradingassistant.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 是无状态的，但账号禁用必须立即生效，因此每次已认证请求都复核用户状态。
 */
@Component
public class ActiveUserFilter extends OncePerRequestFilter {
    private final UserRepository users;

    public ActiveUserFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            boolean active = users.findById(UUID.fromString(jwt.getSubject()))
                    .map(user -> user.getStatus() == User.Status.ACTIVE)
                    .orElse(false);
            if (!active) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/problem+json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(
                        "{\"title\":\"账号不可用\",\"status\":401,\"detail\":\"账号已停用或不存在\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
