package com.example.analytics.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class QueryRateLimitService {

    private final JdbcTemplate jdbcTemplate;

    public QueryRateLimitService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public boolean allow(UUID tenantId, int perMinuteLimit) {
        Instant windowStart = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        Integer counter = jdbcTemplate.queryForObject("""
                INSERT INTO analytics_query_rate_limit_counters (tenant_id, window_start, counter)
                VALUES (?, ?, 1)
                ON CONFLICT (tenant_id, window_start)
                DO UPDATE SET counter = analytics_query_rate_limit_counters.counter + 1
                RETURNING counter
                """, Integer.class, tenantId, windowStart);
        jdbcTemplate.update("""
                DELETE FROM analytics_query_rate_limit_counters
                WHERE tenant_id = ? AND window_start < ? - interval '5 minutes'
                """, tenantId, windowStart);
        return counter != null && counter <= perMinuteLimit;
    }
}
