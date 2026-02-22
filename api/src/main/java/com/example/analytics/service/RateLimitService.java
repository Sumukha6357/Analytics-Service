package com.example.analytics.service;

import com.example.analytics.config.AnalyticsProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class RateLimitService {

    private final JdbcTemplate jdbcTemplate;
    private final AnalyticsProperties analyticsProperties;

    public RateLimitService(JdbcTemplate jdbcTemplate, AnalyticsProperties analyticsProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.analyticsProperties = analyticsProperties;
    }

    public boolean tryConsume(UUID tenantId, int tokens) {
        Instant windowStart = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        Integer count = jdbcTemplate.queryForObject("""
                INSERT INTO rate_limit_counters (tenant_id, window_start, counter)
                VALUES (?, ?, ?)
                ON CONFLICT (tenant_id, window_start)
                DO UPDATE SET counter = rate_limit_counters.counter + EXCLUDED.counter
                RETURNING counter
                """, Integer.class, tenantId, windowStart, tokens);

        Integer limit = jdbcTemplate.queryForObject(
                "SELECT COALESCE(rate_limit_per_min, ?) FROM tenants WHERE id = ?",
                Integer.class,
                analyticsProperties.getRateLimitEventsPerMin(), tenantId);

        if (count == null || limit == null) {
            return false;
        }
        return count <= limit;
    }
}
