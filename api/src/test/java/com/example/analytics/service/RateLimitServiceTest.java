package com.example.analytics.service;

import com.example.analytics.config.AnalyticsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RateLimitServiceTest {

    @Test
    void allowsWithinLimitAndBlocksBeyondLimit() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        when(jdbcTemplate.queryForObject(startsWith("INSERT INTO rate_limit_counters"), eq(Integer.class), any(), any(), anyInt()))
                .thenReturn(5999)
                .thenReturn(6001);
        when(jdbcTemplate.queryForObject(startsWith("SELECT COALESCE(rate_limit_per_min"), eq(Integer.class), anyInt(), any()))
                .thenReturn(6000);

        RateLimitService service = new RateLimitService(jdbcTemplate, properties);
        UUID tenantId = UUID.randomUUID();

        assertTrue(service.tryConsume(tenantId, 1));
        assertFalse(service.tryConsume(tenantId, 1));
    }
}
