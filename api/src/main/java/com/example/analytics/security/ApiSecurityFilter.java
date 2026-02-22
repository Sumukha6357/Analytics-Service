package com.example.analytics.security;

import com.example.analytics.config.AnalyticsProperties;
import com.example.analytics.service.ApiKeyService;
import com.example.analytics.service.AuditLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;

@Component
public class ApiSecurityFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String ADMIN_KEY_HEADER = "X-Admin-Key";

    private final ApiKeyService apiKeyService;
    private final AnalyticsProperties analyticsProperties;
    private final AuditLogService auditLogService;

    public ApiSecurityFilter(ApiKeyService apiKeyService,
                             AnalyticsProperties analyticsProperties,
                             AuditLogService auditLogService) {
        this.apiKeyService = apiKeyService;
        this.analyticsProperties = analyticsProperties;
        this.auditLogService = auditLogService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/actuator/health") || path.equals("/v1/health") || path.startsWith("/actuator/prometheus") || path.startsWith("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        try {
            if (path.startsWith("/admin")) {
                String adminKey = request.getHeader(ADMIN_KEY_HEADER);
                if (!constantTimeEquals(adminKey, analyticsProperties.getAdminApiKey())) {
                    auditLogService.log(null, "admin", "ADMIN_AUTH_FAILED", false,
                            request.getRemoteAddr(), request.getHeader("User-Agent"), org.slf4j.MDC.get("correlationId"),
                            Map.of("path", path));
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid admin key");
                    return;
                }
            } else if (path.startsWith("/v1")) {
                String apiKey = request.getHeader(API_KEY_HEADER);
                if (apiKey == null || apiKey.isBlank()) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing API key");
                    return;
                }
                Optional<AuthPrincipal> principal = apiKeyService.authenticateTenantApiKey(apiKey);
                if (principal.isEmpty()) {
                    auditLogService.log(null, "api-key", "TENANT_AUTH_FAILED", false,
                            request.getRemoteAddr(), request.getHeader("User-Agent"), org.slf4j.MDC.get("correlationId"),
                            Map.of("path", path));
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
                    return;
                }
                TenantContext.set(principal.get());
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
