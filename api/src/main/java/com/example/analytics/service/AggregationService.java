package com.example.analytics.service;

import com.example.analytics.config.AnalyticsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AggregationService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AnalyticsProperties analyticsProperties;

    public AggregationService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, AnalyticsProperties analyticsProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.analyticsProperties = analyticsProperties;
    }

    @Transactional
    public void processMinuteJob(UUID tenantId, String payload) {
        Instant bucketStart = parseInstant(payload, "bucketStart").truncatedTo(ChronoUnit.MINUTES);
        Instant bucketEnd = bucketStart.plus(1, ChronoUnit.MINUTES);

        jdbcTemplate.update("""
                INSERT INTO aggregates_event_counts_minute (tenant_id, bucket_start, event_name, count)
                SELECT tenant_id, date_trunc('minute', received_at), event_name, count(*)
                FROM events_raw
                WHERE tenant_id = ? AND received_at >= ? AND received_at < ?
                GROUP BY tenant_id, date_trunc('minute', received_at), event_name
                ON CONFLICT (tenant_id, bucket_start, event_name)
                DO UPDATE SET count = EXCLUDED.count
                """, tenantId, bucketStart, bucketEnd);

        jdbcTemplate.update("""
                INSERT INTO daily_active_users_set (tenant_id, day, user_key)
                SELECT tenant_id, (event_ts AT TIME ZONE 'UTC')::date,
                       COALESCE(NULLIF(user_id, ''), anonymous_id)
                FROM events_raw
                WHERE tenant_id = ? AND received_at >= ? AND received_at < ?
                  AND COALESCE(NULLIF(user_id, ''), anonymous_id) IS NOT NULL
                ON CONFLICT DO NOTHING
                """, tenantId, bucketStart, bucketEnd);

        jdbcTemplate.update("""
                INSERT INTO daily_event_users (tenant_id, day, event_name, user_key)
                SELECT tenant_id, (event_ts AT TIME ZONE 'UTC')::date, event_name,
                       COALESCE(NULLIF(user_id, ''), anonymous_id)
                FROM events_raw
                WHERE tenant_id = ? AND received_at >= ? AND received_at < ?
                  AND COALESCE(NULLIF(user_id, ''), anonymous_id) IS NOT NULL
                ON CONFLICT DO NOTHING
                """, tenantId, bucketStart, bucketEnd);

        rollupEventCounts(tenantId, bucketStart);
        rollupRevenue(tenantId, bucketStart);
    }

    @Transactional
    public void processDailyJob(UUID tenantId, String payload) {
        LocalDate day = parseInstant(payload, "dayStart").atZone(ZoneOffset.UTC).toLocalDate();

        jdbcTemplate.update("""
                INSERT INTO weekly_active_users_set (tenant_id, week_start, user_key)
                SELECT tenant_id, date_trunc('week', day::timestamp)::date, user_key
                FROM daily_active_users_set
                WHERE tenant_id = ? AND day = ?
                ON CONFLICT DO NOTHING
                """, tenantId, day);

        jdbcTemplate.update("""
                INSERT INTO monthly_active_users_set (tenant_id, month_start, user_key)
                SELECT tenant_id, date_trunc('month', day::timestamp)::date, user_key
                FROM daily_active_users_set
                WHERE tenant_id = ? AND day = ?
                ON CONFLICT DO NOTHING
                """, tenantId, day);

        jdbcTemplate.update("""
                INSERT INTO aggregates_active_users_daily (tenant_id, day, dau, wau, mau)
                VALUES (?, ?,
                    (SELECT count(*) FROM daily_active_users_set WHERE tenant_id = ? AND day = ?),
                    (SELECT count(DISTINCT user_key) FROM daily_active_users_set WHERE tenant_id = ? AND day BETWEEN ? AND ?),
                    (SELECT count(DISTINCT user_key) FROM daily_active_users_set WHERE tenant_id = ? AND day BETWEEN ? AND ?)
                )
                ON CONFLICT (tenant_id, day)
                DO UPDATE SET dau = EXCLUDED.dau, wau = EXCLUDED.wau, mau = EXCLUDED.mau
                """, tenantId, day,
                tenantId, day,
                tenantId, day.minusDays(6), day,
                tenantId, day.minusDays(29), day);

        computeFunnels(tenantId, day);
        computeRetention(tenantId, day);
    }

    private void rollupEventCounts(UUID tenantId, Instant minuteBucket) {
        Instant hour = minuteBucket.truncatedTo(ChronoUnit.HOURS);
        LocalDate day = minuteBucket.atZone(ZoneOffset.UTC).toLocalDate();
        jdbcTemplate.update("""
                INSERT INTO aggregates_event_counts_hour (tenant_id, bucket_start, event_name, count)
                SELECT tenant_id, date_trunc('hour', bucket_start), event_name, sum(count)
                FROM aggregates_event_counts_minute
                WHERE tenant_id = ? AND bucket_start >= ? AND bucket_start < ?
                GROUP BY tenant_id, date_trunc('hour', bucket_start), event_name
                ON CONFLICT (tenant_id, bucket_start, event_name)
                DO UPDATE SET count = EXCLUDED.count
                """, tenantId, hour, hour.plus(1, ChronoUnit.HOURS));

        jdbcTemplate.update("""
                INSERT INTO aggregates_event_counts_day (tenant_id, bucket_start, event_name, count)
                SELECT tenant_id, (bucket_start AT TIME ZONE 'UTC')::date, event_name, sum(count)
                FROM aggregates_event_counts_minute
                WHERE tenant_id = ? AND bucket_start >= ? AND bucket_start < ?
                GROUP BY tenant_id, (bucket_start AT TIME ZONE 'UTC')::date, event_name
                ON CONFLICT (tenant_id, bucket_start, event_name)
                DO UPDATE SET count = EXCLUDED.count
                """, tenantId, day.atStartOfDay().toInstant(ZoneOffset.UTC), day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    private void rollupRevenue(UUID tenantId, Instant minuteBucket) {
        Instant hour = minuteBucket.truncatedTo(ChronoUnit.HOURS);
        Instant hourEnd = hour.plus(1, ChronoUnit.HOURS);
        LocalDate day = minuteBucket.atZone(ZoneOffset.UTC).toLocalDate();

        jdbcTemplate.update("""
                INSERT INTO aggregates_revenue_hourly (tenant_id, bucket_start, currency, revenue, orders)
                SELECT tenant_id,
                       date_trunc('hour', event_ts),
                       COALESCE(NULLIF(properties->>'currency', ''), 'USD') AS currency,
                       COALESCE(sum((properties->>'amount')::numeric), 0)::numeric(18,2),
                       count(*)
                FROM events_raw
                WHERE tenant_id = ?
                  AND event_name = ?
                  AND event_ts >= ?
                  AND event_ts < ?
                  AND (properties->>'amount') ~ '^[0-9]+(\\.[0-9]+)?$'
                GROUP BY tenant_id, date_trunc('hour', event_ts), COALESCE(NULLIF(properties->>'currency', ''), 'USD')
                ON CONFLICT (tenant_id, bucket_start, currency)
                DO UPDATE SET revenue = EXCLUDED.revenue, orders = EXCLUDED.orders
                """, tenantId, analyticsProperties.getRevenueEventName(), hour, hourEnd);

        jdbcTemplate.update("""
                INSERT INTO aggregates_revenue_daily (tenant_id, bucket_start, currency, revenue, orders)
                SELECT tenant_id,
                       (bucket_start AT TIME ZONE 'UTC')::date,
                       currency,
                       sum(revenue),
                       sum(orders)
                FROM aggregates_revenue_hourly
                WHERE tenant_id = ?
                  AND bucket_start >= ?
                  AND bucket_start < ?
                GROUP BY tenant_id, (bucket_start AT TIME ZONE 'UTC')::date, currency
                ON CONFLICT (tenant_id, bucket_start, currency)
                DO UPDATE SET revenue = EXCLUDED.revenue, orders = EXCLUDED.orders
                """, tenantId, day.atStartOfDay().toInstant(ZoneOffset.UTC), day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    private void computeFunnels(UUID tenantId, LocalDate day) {
        List<FunnelDef> funnels = jdbcTemplate.query("""
                SELECT id, steps::text FROM funnels_definitions WHERE tenant_id = ?
                """, (rs, rowNum) -> new FunnelDef(rs.getObject("id", UUID.class), rs.getString("steps")), tenantId);

        for (FunnelDef funnel : funnels) {
            List<String> steps = parseStepNames(funnel.stepsJson());
            if (steps.isEmpty()) {
                continue;
            }

            String prevCte = "step0";
            jdbcTemplate.update("""
                    INSERT INTO aggregates_funnel_runs_daily (tenant_id, funnel_id, day, step_index, users)
                    SELECT ?, ?, ?, 0, count(*)
                    FROM daily_event_users
                    WHERE tenant_id = ? AND day = ? AND event_name = ?
                    ON CONFLICT (tenant_id, funnel_id, day, step_index)
                    DO UPDATE SET users = EXCLUDED.users
                    """, tenantId, funnel.id(), day, tenantId, day, steps.get(0));

            for (int i = 1; i < steps.size(); i++) {
                Long users = jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM (
                            SELECT u.user_key
                            FROM daily_event_users u
                            JOIN daily_event_users p ON p.tenant_id = u.tenant_id
                                AND p.day = u.day
                                AND p.user_key = u.user_key
                            WHERE u.tenant_id = ? AND u.day = ?
                              AND u.event_name = ?
                              AND p.event_name = ?
                            GROUP BY u.user_key
                        ) x
                        """, Long.class, tenantId, day, steps.get(i), steps.get(i - 1));

                jdbcTemplate.update("""
                        INSERT INTO aggregates_funnel_runs_daily (tenant_id, funnel_id, day, step_index, users)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT (tenant_id, funnel_id, day, step_index)
                        DO UPDATE SET users = EXCLUDED.users
                        """, tenantId, funnel.id(), day, i, users == null ? 0L : users);
            }
        }
    }

    private void computeRetention(UUID tenantId, LocalDate day) {
        List<RetentionDef> retentions = jdbcTemplate.query("""
                SELECT id, cohort_event, return_event, window_days
                FROM retention_definitions
                WHERE tenant_id = ?
                """, (rs, rowNum) -> new RetentionDef(
                rs.getObject("id", UUID.class),
                rs.getString("cohort_event"),
                rs.getString("return_event"),
                rs.getInt("window_days")
        ), tenantId);

        for (RetentionDef retention : retentions) {
            jdbcTemplate.update("""
                    INSERT INTO cohort_users (tenant_id, retention_id, cohort_day, user_key)
                    SELECT tenant_id, ?, day, user_key
                    FROM daily_event_users
                    WHERE tenant_id = ? AND day = ? AND event_name = ?
                    ON CONFLICT DO NOTHING
                    """, retention.id(), tenantId, day, retention.cohortEvent());

            jdbcTemplate.update("""
                    INSERT INTO return_users (tenant_id, retention_id, return_day, user_key)
                    SELECT tenant_id, ?, day, user_key
                    FROM daily_event_users
                    WHERE tenant_id = ? AND day BETWEEN ? AND ? AND event_name = ?
                    ON CONFLICT DO NOTHING
                    """, retention.id(), tenantId, day, day.plusDays(retention.windowDays()), retention.returnEvent());

            for (int offset = 0; offset <= retention.windowDays(); offset++) {
                LocalDate returnDay = day.plusDays(offset);
                Long users = jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM (
                            SELECT c.user_key
                            FROM cohort_users c
                            JOIN return_users r
                              ON r.tenant_id = c.tenant_id
                             AND r.retention_id = c.retention_id
                             AND r.user_key = c.user_key
                            WHERE c.tenant_id = ?
                              AND c.retention_id = ?
                              AND c.cohort_day = ?
                              AND r.return_day = ?
                            GROUP BY c.user_key
                        ) x
                        """, Long.class, tenantId, retention.id(), day, returnDay);

                jdbcTemplate.update("""
                        INSERT INTO aggregates_retention_cohorts (tenant_id, retention_id, cohort_day, day_offset, users)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT (tenant_id, retention_id, cohort_day, day_offset)
                        DO UPDATE SET users = EXCLUDED.users
                        """, tenantId, retention.id(), day, offset, users == null ? 0L : users);
            }
        }
    }

    @Transactional
    public void retentionCleanup() {
        LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(analyticsProperties.getRetentionRawDays());
        List<String> partitions = jdbcTemplate.queryForList("""
                SELECT child.relname AS partition_name
                FROM pg_inherits
                JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
                JOIN pg_class child ON pg_inherits.inhrelid = child.oid
                WHERE parent.relname = 'events_raw'
                """, String.class);

        for (String partitionName : partitions) {
            if (partitionName != null && partitionName.startsWith("events_raw_") && partitionName.length() == 19) {
                LocalDate day = LocalDate.parse(partitionName.substring("events_raw_".length()), java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
                if (day.isBefore(cutoff)) {
                    jdbcTemplate.queryForList("SELECT drop_events_partition(?)", day);
                }
            }
        }

        jdbcTemplate.update("DELETE FROM aggregates_event_counts_minute WHERE bucket_start < ?", cutoff.atStartOfDay().toInstant(ZoneOffset.UTC));
        jdbcTemplate.update("DELETE FROM aggregates_event_counts_hour WHERE bucket_start < ?", cutoff.atStartOfDay().toInstant(ZoneOffset.UTC));
        jdbcTemplate.update("DELETE FROM aggregates_event_counts_day WHERE bucket_start < ?", cutoff);
        jdbcTemplate.update("DELETE FROM aggregates_revenue_hourly WHERE bucket_start < ?", cutoff.atStartOfDay().toInstant(ZoneOffset.UTC));
        jdbcTemplate.update("DELETE FROM aggregates_revenue_daily WHERE bucket_start < ?", cutoff);
        jdbcTemplate.update("DELETE FROM aggregates_active_users_daily WHERE day < ?", cutoff);
        jdbcTemplate.update("DELETE FROM daily_active_users_set WHERE day < ?", cutoff);
        jdbcTemplate.update("DELETE FROM daily_event_users WHERE day < ?", cutoff);
        jdbcTemplate.update("DELETE FROM cohort_users WHERE cohort_day < ?", cutoff);
        jdbcTemplate.update("DELETE FROM return_users WHERE return_day < ?", cutoff);
        jdbcTemplate.update("DELETE FROM weekly_active_users_set WHERE week_start < ?", cutoff.minusDays(7));
        jdbcTemplate.update("DELETE FROM monthly_active_users_set WHERE month_start < ?", cutoff.minusDays(30));
    }

    @Transactional
    public void maintainPartitions() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int i = 0; i <= analyticsProperties.getPartitionPrecreateDays(); i++) {
            jdbcTemplate.queryForList("SELECT create_events_partition(?)", today.plusDays(i));
        }

        if (analyticsProperties.isEnableGinProperties()) {
            List<String> partitions = jdbcTemplate.queryForList("""
                    SELECT child.relname AS partition_name
                    FROM pg_inherits
                    JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
                    JOIN pg_class child ON pg_inherits.inhrelid = child.oid
                    WHERE parent.relname = 'events_raw'
                    """, String.class);
            for (String partition : partitions) {
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS " + partition + "_properties_gin ON " + partition + " USING GIN (properties)");
            }
        }
    }

    private Instant parseInstant(String payload, String field) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String value = node.path(field).asText(null);
            if (value == null) {
                throw new IllegalArgumentException("Missing field: " + field);
            }
            return Instant.parse(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid payload: " + payload, e);
        }
    }

    private List<String> parseStepNames(String stepsJson) {
        try {
            JsonNode array = objectMapper.readTree(stepsJson);
            List<String> steps = new ArrayList<>();
            for (JsonNode node : array) {
                if (node.hasNonNull("eventName")) {
                    steps.add(node.get("eventName").asText());
                }
            }
            return steps;
        } catch (Exception e) {
            throw new IllegalStateException("Invalid funnel steps JSON", e);
        }
    }

    private record FunnelDef(UUID id, String stepsJson) {
    }

    private record RetentionDef(UUID id, String cohortEvent, String returnEvent, int windowDays) {
    }
}
