package com.example.analytics.integration;

import com.example.analytics.service.AggregationService;
import com.example.analytics.service.ApiKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AnalyticsIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("analytics.admin-api-key", () -> "admin-secret");
        registry.add("analytics.retention-raw-days", () -> 1);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    AggregationService aggregationService;

    UUID tenantId;
    String apiKey;

    @BeforeEach
    void setup() {
        jdbcTemplate.execute("TRUNCATE TABLE analytics_query_rate_limit_counters, audit_logs, events_registry, cohort_users, return_users, aggregates_retention_cohorts, aggregates_funnel_runs_daily, retention_definitions, funnels_definitions, aggregates_revenue_daily, aggregates_revenue_hourly, monthly_active_users_set, weekly_active_users_set, daily_event_users, aggregates_active_users_daily, daily_active_users_set, aggregates_event_counts_day, aggregates_event_counts_hour, aggregates_event_counts_minute, job_queue, rate_limit_counters, ingestion_idempotency, tenant_api_keys, tenants CASCADE");
        tenantId = UUID.randomUUID();
        apiKey = "ak_test_" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tenants (id, name, status) VALUES (?, 'tenant', 'active')", tenantId);
        jdbcTemplate.update("INSERT INTO tenant_api_keys (id, tenant_id, key_hash, name) VALUES (?, ?, ?, 'default')",
                UUID.randomUUID(), tenantId, ApiKeyService.sha256(apiKey));
        jdbcTemplate.update("SELECT create_events_partition(?)", LocalDate.now());
        jdbcTemplate.update("SELECT create_events_partition(?)", LocalDate.now().minusDays(2));
    }

    @Test
    void trackInsertsRawAndJob() throws Exception {
        mockMvc.perform(post("/v1/track")
                        .header("X-API-Key", apiKey)
                        .contentType("application/json")
                        .content("""
                                {"event":"OrderPlaced","userId":"u1","timestamp":"2026-02-22T10:20:30Z","properties":{"amount":"10.00","currency":"USD"},"context":{}}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.ingestionId").exists());

        Integer events = jdbcTemplate.queryForObject("SELECT count(*) FROM events_raw WHERE tenant_id = ?", Integer.class, tenantId);
        Integer jobs = jdbcTemplate.queryForObject("SELECT count(*) FROM job_queue WHERE tenant_id = ? AND job_type = 'AGG_MINUTE'", Integer.class, tenantId);

        org.junit.jupiter.api.Assertions.assertEquals(1, events);
        org.junit.jupiter.api.Assertions.assertTrue(jobs >= 1);
    }

    @Test
    void batchIngestReturnsDedupedCounts() throws Exception {
        UUID eventId = UUID.randomUUID();
        mockMvc.perform(post("/v1/batch")
                        .header("X-API-Key", apiKey)
                        .header("Idempotency-Key", "idem-batch-1")
                        .contentType("application/json")
                        .content("""
                                {"events":[
                                  {"event":"OrderPlaced","eventId":"%s","userId":"u1","timestamp":"2026-02-22T10:20:30Z","schemaVersion":1,"properties":{"amount":"10.00","currency":"USD"},"context":{}},
                                  {"event":"OrderPlaced","eventId":"%s","userId":"u1","timestamp":"2026-02-22T10:20:31Z","schemaVersion":1,"properties":{"amount":"10.00","currency":"USD"},"context":{}}
                                ]}
                                """.formatted(eventId, eventId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.deduped").value(1));
    }

    @Test
    void aggMinuteUpdatesAggregates() {
        Instant bucket = Instant.parse("2026-02-22T10:20:00Z");
        jdbcTemplate.update("SELECT create_events_partition(?)", LocalDate.parse("2026-02-22"));
        jdbcTemplate.update("""
                INSERT INTO events_raw (tenant_id, received_at, event_ts, event_name, user_id, schema_version, properties, context, ingestion_id)
                VALUES (?, ?, ?, 'OrderPlaced', 'u42', 1, '{"amount":"20.00","currency":"USD"}'::jsonb, '{}'::jsonb, ?)
                """, tenantId, bucket.plusSeconds(1), bucket.plusSeconds(1), UUID.randomUUID());

        aggregationService.processMinuteJob(tenantId, "{\"bucketStart\":\"2026-02-22T10:20:00Z\"}");

        Long count = jdbcTemplate.queryForObject("SELECT count FROM aggregates_event_counts_minute WHERE tenant_id = ? AND bucket_start = ? AND event_name='OrderPlaced'", Long.class, tenantId, bucket);
        Long dau = jdbcTemplate.queryForObject("SELECT dau FROM aggregates_active_users_daily WHERE tenant_id = ? AND day = '2026-02-22'", Long.class, tenantId);

        org.junit.jupiter.api.Assertions.assertEquals(1L, count);
        org.junit.jupiter.api.Assertions.assertEquals(1L, dau);
    }

    @Test
    void queryEndpointsReturnAggregateData() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO aggregates_event_counts_minute (tenant_id, bucket_start, event_name, count)
                VALUES (?, '2026-02-22T10:20:00Z', 'OrderPlaced', 5)
                """, tenantId);

        mockMvc.perform(get("/v1/metrics/event-counts")
                        .header("X-API-Key", apiKey)
                        .queryParam("from", "2026-02-22T10:00:00Z")
                        .queryParam("to", "2026-02-22T11:00:00Z")
                        .queryParam("interval", "minute")
                        .queryParam("event", "OrderPlaced"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value", greaterThanOrEqualTo(5)));
    }

    @Test
    void partitionMaintCreatesFuturePartition() {
        aggregationService.maintainPartitions();
        String partitionName = "events_raw_" + LocalDate.now().plusDays(1).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        Boolean exists = jdbcTemplate.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, partitionName);
        org.junit.jupiter.api.Assertions.assertTrue(Boolean.TRUE.equals(exists));
    }

    @Test
    void retentionCleanupDropsOldPartition() {
        String oldPartition = "events_raw_" + LocalDate.now().minusDays(2).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        aggregationService.retentionCleanup();
        Boolean exists = jdbcTemplate.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, oldPartition);
        org.junit.jupiter.api.Assertions.assertFalse(Boolean.TRUE.equals(exists));
    }
}
