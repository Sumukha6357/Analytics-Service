package com.example.analytics.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MetricsQueryService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<RedisService> redisServiceProvider;

    public MetricsQueryService(JdbcTemplate jdbcTemplate, ObjectProvider<RedisService> redisServiceProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisServiceProvider = redisServiceProvider;
    }

    public List<Map<String, Object>> eventCounts(UUID tenantId, Instant from, Instant to, String interval, String eventName) {
        return switch (interval) {
            case "minute" -> jdbcTemplate.queryForList("""
                    SELECT bucket_start, count AS value
                    FROM aggregates_event_counts_minute
                    WHERE tenant_id = ?
                      AND bucket_start >= ?
                      AND bucket_start < ?
                      AND event_name = ?
                    ORDER BY bucket_start
                    """, tenantId, from, to, eventName);
            case "hour" -> jdbcTemplate.queryForList("""
                    SELECT bucket_start, count AS value
                    FROM aggregates_event_counts_hour
                    WHERE tenant_id = ?
                      AND bucket_start >= ?
                      AND bucket_start < ?
                      AND event_name = ?
                    ORDER BY bucket_start
                    """, tenantId, from, to, eventName);
            case "day" -> jdbcTemplate.queryForList("""
                    SELECT bucket_start::timestamp AS bucket_start, count AS value
                    FROM aggregates_event_counts_day
                    WHERE tenant_id = ?
                      AND bucket_start >= ?
                      AND bucket_start < ?
                      AND event_name = ?
                    ORDER BY bucket_start
                    """, tenantId, from.atZone(ZoneOffset.UTC).toLocalDate(), to.atZone(ZoneOffset.UTC).toLocalDate(), eventName);
            default -> throw new IllegalArgumentException("Unsupported interval");
        };
    }

    public List<Map<String, Object>> dau(UUID tenantId, LocalDate from, LocalDate to) {
        return jdbcTemplate.queryForList("""
                SELECT day, dau, wau, mau
                FROM aggregates_active_users_daily
                WHERE tenant_id = ? AND day BETWEEN ? AND ?
                ORDER BY day
                """, tenantId, from, to);
    }

    public List<Map<String, Object>> revenue(UUID tenantId, Instant from, Instant to, String interval, String currency) {
        if ("hour".equals(interval)) {
            return jdbcTemplate.queryForList("""
                    SELECT bucket_start, revenue, orders
                    FROM aggregates_revenue_hourly
                    WHERE tenant_id = ?
                      AND bucket_start >= ?
                      AND bucket_start < ?
                      AND currency = ?
                    ORDER BY bucket_start
                    """, tenantId, from, to, currency);
        }
        return jdbcTemplate.queryForList("""
                SELECT bucket_start::timestamp AS bucket_start, revenue, orders
                FROM aggregates_revenue_daily
                WHERE tenant_id = ?
                  AND bucket_start >= ?
                  AND bucket_start < ?
                  AND currency = ?
                ORDER BY bucket_start
                """, tenantId, from.atZone(ZoneOffset.UTC).toLocalDate(), to.atZone(ZoneOffset.UTC).toLocalDate(), currency);
    }

    public List<Map<String, Object>> funnel(UUID tenantId, UUID funnelId, LocalDate from, LocalDate to) {
        return jdbcTemplate.queryForList("""
                SELECT day, step_index, users
                FROM aggregates_funnel_runs_daily
                WHERE tenant_id = ? AND funnel_id = ? AND day BETWEEN ? AND ?
                ORDER BY day, step_index
                """, tenantId, funnelId, from, to);
    }

    public List<Map<String, Object>> retention(UUID tenantId, UUID retentionId, LocalDate cohortFrom, LocalDate cohortTo) {
        return jdbcTemplate.queryForList("""
                SELECT cohort_day, day_offset, users
                FROM aggregates_retention_cohorts
                WHERE tenant_id = ? AND retention_id = ? AND cohort_day BETWEEN ? AND ?
                ORDER BY cohort_day, day_offset
                """, tenantId, retentionId, cohortFrom, cohortTo);
    }

    public List<Map<String, Object>> listEvents(UUID tenantId, int limit, int offset) {
        return jdbcTemplate.queryForList("""
                SELECT event_name, latest_schema_version, first_seen_at, last_seen_at, total_seen
                FROM events_registry
                WHERE tenant_id = ?
                ORDER BY last_seen_at DESC
                LIMIT ? OFFSET ?
                """, tenantId, limit, offset);
    }

    public Map<String, Object> eventDetail(UUID tenantId, String eventName) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT event_name, latest_schema_version, first_seen_at, last_seen_at, total_seen, sample_properties
                FROM events_registry
                WHERE tenant_id = ? AND event_name = ?
                """, tenantId, eventName);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    public Map<String, Object> systemStatus() {
        Long queueDepth = jdbcTemplate.queryForObject("SELECT count(*) FROM job_queue WHERE status='PENDING'", Long.class);
        Long oldestPending = jdbcTemplate.queryForObject("SELECT COALESCE(EXTRACT(EPOCH FROM (now() - min(not_before))), 0) FROM job_queue WHERE status='PENDING'", Long.class);
        Long ingestionRate = jdbcTemplate.queryForObject("SELECT count(*) FROM events_raw WHERE received_at >= now() - interval '1 minute'", Long.class);

        Integer partitionsReady = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM (
                  SELECT to_regclass(format('events_raw_%s', to_char(d, 'YYYYMMDD'))) AS part
                  FROM generate_series((now() at time zone 'UTC')::date, (now() at time zone 'UTC')::date + interval '2 day', interval '1 day') d
                ) x
                WHERE part IS NOT NULL
                """, Integer.class);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("queueDepth", queueDepth == null ? 0 : queueDepth);
        out.put("oldestPendingJobAgeSeconds", oldestPending == null ? 0 : oldestPending);
        out.put("ingestionRate1m", ingestionRate == null ? 0 : ingestionRate);
        out.put("partitionHealth", Map.of("partitionsPresent", partitionsReady == null ? 0 : partitionsReady));
        RedisService redisService = redisServiceProvider.getIfAvailable();
        out.put("redis", Map.of("available", redisService != null && redisService.isAvailable()));
        return out;
    }
}
