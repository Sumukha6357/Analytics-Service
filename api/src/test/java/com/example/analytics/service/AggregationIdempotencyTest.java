package com.example.analytics.service;

import com.example.analytics.config.AnalyticsProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class AggregationIdempotencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("analytics.admin-api-key", () -> "test-admin");
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    AggregationService aggregationService;

    @Test
    void aggMinuteUpsertIsIdempotent() {
        UUID tenant = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tenants (id, name, status) VALUES (?, ?, 'active')", tenant, "tenant-1");

        Instant now = Instant.parse("2026-02-22T10:20:00Z");
        jdbcTemplate.update("SELECT create_events_partition(?)", java.sql.Date.valueOf("2026-02-22"));
        jdbcTemplate.update("""
                INSERT INTO events_raw (tenant_id, received_at, event_ts, event_name, user_id, anonymous_id, schema_version, properties, context, ingestion_id)
                VALUES (?, ?, ?, 'OrderPlaced', 'u1', NULL, 1, '{"amount":"12.00","currency":"USD"}'::jsonb, '{}'::jsonb, ?)
                """, tenant, now.plusSeconds(1), now.plusSeconds(1), UUID.randomUUID());

        String payload = "{\"bucketStart\":\"2026-02-22T10:20:00Z\"}";
        aggregationService.processMinuteJob(tenant, payload);
        aggregationService.processMinuteJob(tenant, payload);

        Long value = jdbcTemplate.queryForObject("""
                SELECT count FROM aggregates_event_counts_minute
                WHERE tenant_id = ? AND bucket_start = ? AND event_name = 'OrderPlaced'
                """, Long.class, tenant, now);
        assertEquals(1L, value);
    }
}
