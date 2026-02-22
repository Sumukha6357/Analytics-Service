package com.example.analytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ExecutiveAnalyticsService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ExecutiveAnalyticsService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Cacheable(value = "executive-dashboard", key = "#tenantId.toString() + ':' + #from.toString() + ':' + #to.toString() + ':' + #owner + ':' + #pipeline + ':' + #segment + ':' + #role + ':' + #actor")
    public Map<String, Object> dashboard(UUID tenantId,
                                         Instant from,
                                         Instant to,
                                         String owner,
                                         String pipeline,
                                         String segment,
                                         String role,
                                         String actor) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("strategicKpis", strategicKpis(tenantId, from, to, role, actor));
        out.put("revenueTrend", revenueTrend(tenantId, from, to));
        out.put("stageDistribution", stageDistribution(tenantId, from, to));
        out.put("funnel", funnel(tenantId, from, to));
        out.put("winLoss", winLoss(tenantId, from, to));
        out.put("agingMatrix", agingMatrix(tenantId, from, to));
        out.put("forecastConfidence", forecastConfidence(tenantId, from, to));
        out.put("insights", insights(tenantId, from, to));
        out.put("meta", Map.of(
                "role", role,
                "ownerFilter", owner,
                "pipelineFilter", pipeline,
                "segmentFilter", segment,
                "from", from,
                "to", to
        ));
        return out;
    }

    @Cacheable(value = "executive-reps", key = "#tenantId.toString() + ':' + #from.toString() + ':' + #to.toString() + ':' + #owner + ':' + #role + ':' + #actor + ':' + #limit + ':' + #offset")
    public Map<String, Object> reps(UUID tenantId,
                                    Instant from,
                                    Instant to,
                                    String owner,
                                    String role,
                                    String actor,
                                    int limit,
                                    int offset) {
        String ownerScope = scopedOwner(role, actor, owner);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT COALESCE(NULLIF(properties->>'owner',''), NULLIF(user_id,''), 'Unknown') AS rep,
                       count(*) FILTER (WHERE event_name = 'DealWon') AS deals_won,
                       count(*) FILTER (WHERE event_name IN ('DealWon','DealLost')) AS closed,
                       COALESCE(sum((properties->>'amount')::numeric) FILTER (WHERE event_name='DealWon' AND (properties->>'amount') ~ '^[0-9]+(\\.[0-9]+)?$'), 0) AS revenue
                FROM events_raw
                WHERE tenant_id = ?
                  AND received_at >= ?
                  AND received_at < ?
                  AND (? IS NULL OR COALESCE(NULLIF(properties->>'owner',''), NULLIF(user_id,''), 'Unknown') = ?)
                GROUP BY COALESCE(NULLIF(properties->>'owner',''), NULLIF(user_id,''), 'Unknown')
                ORDER BY revenue DESC
                LIMIT ? OFFSET ?
                """, tenantId, from, to, ownerScope, ownerScope, limit, offset);

        List<Map<String, Object>> shaped = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            long won = ((Number) row.getOrDefault("deals_won", 0)).longValue();
            long closed = ((Number) row.getOrDefault("closed", 0)).longValue();
            double rate = closed == 0 ? 0 : (won * 100.0 / closed);
            shaped.add(Map.of(
                    "rep", row.get("rep"),
                    "deals", won,
                    "conversion", String.format("%.1f%%", rate),
                    "revenue", "$" + row.get("revenue"),
                    "velocity", won > 20 ? "28d" : won > 10 ? "34d" : "41d",
                    "status", rate >= 25 ? "excellent" : rate >= 18 ? "stable" : "risk"
            ));
        }

        Long total = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM (
                    SELECT 1
                    FROM events_raw
                    WHERE tenant_id = ?
                      AND received_at >= ?
                      AND received_at < ?
                      AND (? IS NULL OR COALESCE(NULLIF(properties->>'owner',''), NULLIF(user_id,''), 'Unknown') = ?)
                    GROUP BY COALESCE(NULLIF(properties->>'owner',''), NULLIF(user_id,''), 'Unknown')
                ) t
                """, Long.class, tenantId, from, to, ownerScope, ownerScope);

        return Map.of("items", shaped, "total", total == null ? 0 : total);
    }

    @Cacheable(value = "executive-deals", key = "#tenantId.toString() + ':' + #from.toString() + ':' + #to.toString() + ':' + #kind + ':' + #owner + ':' + #pipeline + ':' + #segment + ':' + #role + ':' + #actor + ':' + #limit + ':' + #offset")
    public Map<String, Object> deals(UUID tenantId,
                                     Instant from,
                                     Instant to,
                                     String kind,
                                     String owner,
                                     String pipeline,
                                     String segment,
                                     String role,
                                     String actor,
                                     int limit,
                                     int offset) {
        String ownerScope = scopedOwner(role, actor, owner);
        String dealCondition = "high".equalsIgnoreCase(kind) ? ">=" : "<";
        int threshold = "high".equalsIgnoreCase(kind) ? 100000 : 100000;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT COALESCE(NULLIF(properties->>'dealId',''), md5(id::text)) AS id,
                       COALESCE(NULLIF(properties->>'dealName',''), event_name || '-' || id::text) AS name,
                       COALESCE(NULLIF(properties->>'owner',''), NULLIF(user_id,''), 'Unknown') AS owner,
                       COALESCE(NULLIF(properties->>'segment',''), 'General') AS segment,
                       COALESCE(NULLIF(properties->>'pipeline',''), 'Default') AS pipeline,
                       COALESCE(NULLIF(properties->>'stage',''), 'Qualified') AS stage,
                       COALESCE((properties->>'idleDays')::int, 0) AS idle_days,
                       COALESCE(NULLIF(properties->>'closeDate',''), '') AS close_date,
                       COALESCE((properties->>'amount')::numeric, 0) AS amount
                FROM events_raw
                WHERE tenant_id = ?
                  AND received_at >= ?
                  AND received_at < ?
                  AND event_name IN ('DealUpdated','DealWon','DealLost','OrderPlaced')
                  AND (? IS NULL OR COALESCE(NULLIF(properties->>'owner',''), NULLIF(user_id,''), 'Unknown') = ?)
                  AND (? IS NULL OR COALESCE(NULLIF(properties->>'pipeline',''), 'Default') = ?)
                  AND (? IS NULL OR COALESCE(NULLIF(properties->>'segment',''), 'General') = ?)
                  AND COALESCE((properties->>'amount')::numeric, 0) %s ?
                ORDER BY amount DESC, received_at DESC
                LIMIT ? OFFSET ?
                """.formatted(dealCondition), tenantId, from, to,
                ownerScope, ownerScope,
                nullIfAll(pipeline), nullIfAll(pipeline),
                nullIfAll(segment), nullIfAll(segment),
                threshold, limit, offset);

        List<Map<String, Object>> shaped = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            int idle = ((Number) row.getOrDefault("idle_days", 0)).intValue();
            String status = idle > 20 ? "stalled" : idle > 12 ? "warning" : "on-track";
            if (!"high".equalsIgnoreCase(kind) && "on-track".equals(status)) {
                continue;
            }
            shaped.add(Map.of(
                    "id", row.get("id"),
                    "name", row.get("name"),
                    "owner", row.get("owner"),
                    "segment", row.get("segment"),
                    "pipeline", row.get("pipeline"),
                    "value", "$" + row.get("amount"),
                    "stage", row.get("stage"),
                    "idleDays", idle,
                    "closeDate", String.valueOf(row.get("close_date")).isBlank() ? "TBD" : row.get("close_date"),
                    "status", status
            ));
        }

        return Map.of("items", shaped, "total", shaped.size());
    }

    public List<Map<String, Object>> alerts(UUID tenantId, Instant from, Instant to) {
        return insights(tenantId, from, to);
    }

    public Map<String, Object> createShare(UUID tenantId, Map<String, Object> payload, int expiresMinutes) {
        UUID token = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(Math.max(5, expiresMinutes) * 60L);
        jdbcTemplate.update("""
                INSERT INTO report_shares (token, tenant_id, payload, expires_at)
                VALUES (?, ?, ?::jsonb, ?)
                """, token, tenantId, toJson(payload), expiresAt);
        return Map.of("token", token, "expiresAt", expiresAt);
    }

    public Map<String, Object> resolveShare(UUID tenantId, UUID token) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT payload, expires_at
                FROM report_shares
                WHERE token = ? AND tenant_id = ?
                """, token, tenantId);
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> row = rows.get(0);
        Instant expires = ((java.sql.Timestamp) row.get("expires_at")).toInstant();
        if (expires.isBefore(Instant.now())) {
            return Map.of();
        }
        return Map.of("token", token, "expiresAt", expires, "payload", row.get("payload"));
    }

    public Map<String, Object> createExport(UUID tenantId, String format, String createdBy, Instant from, Instant to, String owner, String pipeline, String segment, String role, String actor) {
        Map<String, Object> dashboard = dashboard(tenantId, from, to, owner, pipeline, segment, role, actor);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> kpis = (List<Map<String, Object>>) dashboard.getOrDefault("strategicKpis", List.of());

        StringBuilder csv = new StringBuilder();
        csv.append("kpi,value,delta\n");
        for (Map<String, Object> kpi : kpis) {
            csv.append(kpi.getOrDefault("title", "")).append(',')
                    .append(kpi.getOrDefault("value", "")).append(',')
                    .append(kpi.getOrDefault("delta", "")).append('\n');
        }

        UUID token = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(900);
        jdbcTemplate.update("""
                INSERT INTO report_exports (token, tenant_id, created_by, format, content, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, token, tenantId, createdBy, format, csv.toString(), expiresAt);

        return Map.of("token", token, "expiresAt", expiresAt);
    }

    public Map<String, Object> resolveExport(UUID tenantId, UUID token) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT format, content, expires_at
                FROM report_exports
                WHERE token = ? AND tenant_id = ?
                """, token, tenantId);
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> row = rows.get(0);
        Instant expires = ((java.sql.Timestamp) row.get("expires_at")).toInstant();
        if (expires.isBefore(Instant.now())) {
            return Map.of();
        }
        return Map.of("format", row.get("format"), "content", row.get("content"), "expiresAt", expires);
    }

    private List<Map<String, Object>> strategicKpis(UUID tenantId, Instant from, Instant to, String role, String actor) {
        Double revenue = jdbcTemplate.queryForObject("""
                SELECT COALESCE(sum(revenue), 0)
                FROM aggregates_revenue_hourly
                WHERE tenant_id = ? AND bucket_start >= ? AND bucket_start < ?
                """, Double.class, tenantId, from, to);

        Long leads = jdbcTemplate.queryForObject("""
                SELECT COALESCE(sum(count), 0)
                FROM aggregates_event_counts_day
                WHERE tenant_id = ? AND bucket_start >= ? AND bucket_start < ? AND event_name IN ('LeadCreated', 'UserSignedUp')
                """, Long.class, tenantId, from.atZone(ZoneOffset.UTC).toLocalDate(), to.atZone(ZoneOffset.UTC).toLocalDate());

        Long won = jdbcTemplate.queryForObject("""
                SELECT COALESCE(sum(count), 0)
                FROM aggregates_event_counts_day
                WHERE tenant_id = ? AND bucket_start >= ? AND bucket_start < ? AND event_name IN ('DealWon', 'OrderPlaced')
                """, Long.class, tenantId, from.atZone(ZoneOffset.UTC).toLocalDate(), to.atZone(ZoneOffset.UTC).toLocalDate());

        long leadCount = leads == null ? 0 : leads;
        long wonCount = won == null ? 0 : won;
        double conversion = leadCount == 0 ? 0 : (wonCount * 100.0 / leadCount);
        double totalRevenue = revenue == null ? 0 : revenue;
        double forecast = totalRevenue * 1.08;

        List<Map<String, Object>> list = new ArrayList<>();
        list.add(Map.of("id", "total-revenue", "title", "Total Revenue", "value", currency(totalRevenue), "delta", 8.2));
        list.add(Map.of("id", "forecast-revenue", "title", "Forecasted Revenue", "value", currency(forecast), "delta", 5.4));
        list.add(Map.of("id", "conversion-rate", "title", "Conversion Rate", "value", String.format("%.1f%%", conversion), "delta", -1.3));
        list.add(Map.of("id", "active-deals", "title", "Active Deals", "value", String.valueOf(Math.max(0, leadCount - wonCount)), "delta", 3.1));

        if (!"rep".equalsIgnoreCase(role)) {
            list.add(Map.of("id", "avg-sales-cycle", "title", "Avg Sales Cycle", "value", "34 days", "delta", -4.7));
            list.add(Map.of("id", "sales-velocity", "title", "Sales Velocity", "value", currency(totalRevenue / 12), "delta", 6.8));
        }
        return list;
    }

    private List<Map<String, Object>> revenueTrend(UUID tenantId, Instant from, Instant to) {
        return jdbcTemplate.queryForList("""
                SELECT bucket_start::date AS date,
                       COALESCE(sum(revenue),0) AS revenue,
                       COALESCE(sum(revenue),0) * 0.95 AS target,
                       COALESCE(sum(revenue),0) / 30 AS mrr,
                       COALESCE(sum(revenue),0) / 30 * 12 AS arr
                FROM aggregates_revenue_hourly
                WHERE tenant_id = ? AND bucket_start >= ? AND bucket_start < ?
                GROUP BY bucket_start::date
                ORDER BY bucket_start::date
                """, tenantId, from, to);
    }

    private List<Map<String, Object>> stageDistribution(UUID tenantId, Instant from, Instant to) {
        return jdbcTemplate.queryForList("""
                SELECT event_name AS stage, COALESCE(sum(count),0) AS value
                FROM aggregates_event_counts_day
                WHERE tenant_id = ?
                  AND bucket_start >= ?
                  AND bucket_start < ?
                GROUP BY event_name
                ORDER BY value DESC
                LIMIT 6
                """, tenantId, from.atZone(ZoneOffset.UTC).toLocalDate(), to.atZone(ZoneOffset.UTC).toLocalDate());
    }

    private List<Map<String, Object>> funnel(UUID tenantId, Instant from, Instant to) {
        LocalDate day = to.atZone(ZoneOffset.UTC).toLocalDate().minusDays(1);
        return List.of(
                Map.of("step", "Leads", "value", countEvent(tenantId, from, to, "LeadCreated")),
                Map.of("step", "Qualified", "value", countEvent(tenantId, from, to, "LeadQualified")),
                Map.of("step", "Proposal", "value", countEvent(tenantId, from, to, "ProposalSent")),
                Map.of("step", "Won", "value", countEvent(tenantId, from, to, "DealWon"))
        );
    }

    private List<Map<String, Object>> winLoss(UUID tenantId, Instant from, Instant to) {
        long won = countEvent(tenantId, from, to, "DealWon");
        long lost = countEvent(tenantId, from, to, "DealLost");
        long open = Math.max(0, countEvent(tenantId, from, to, "LeadCreated") - won - lost);
        long total = Math.max(1, won + lost + open);
        return List.of(
                Map.of("name", "Won", "value", Math.round((won * 100.0) / total)),
                Map.of("name", "Lost", "value", Math.round((lost * 100.0) / total)),
                Map.of("name", "Open", "value", Math.round((open * 100.0) / total))
        );
    }

    private List<Map<String, Object>> agingMatrix(UUID tenantId, Instant from, Instant to) {
        return jdbcTemplate.queryForList("""
                SELECT COALESCE(NULLIF(properties->>'stage',''), 'Qualified') AS stage,
                       CASE
                         WHEN COALESCE((properties->>'idleDays')::int, 0) <= 14 THEN '0-14d'
                         WHEN COALESCE((properties->>'idleDays')::int, 0) <= 30 THEN '15-30d'
                         ELSE '31+d'
                       END AS bucket,
                       count(*) AS count,
                       CASE
                         WHEN COALESCE((properties->>'idleDays')::int, 0) <= 14 THEN 'low'
                         WHEN COALESCE((properties->>'idleDays')::int, 0) <= 30 THEN 'medium'
                         ELSE 'high'
                       END AS risk
                FROM events_raw
                WHERE tenant_id = ?
                  AND received_at >= ?
                  AND received_at < ?
                  AND event_name IN ('DealUpdated','DealWon','DealLost')
                GROUP BY stage, bucket, risk
                ORDER BY stage, bucket
                """, tenantId, from, to);
    }

    private int forecastConfidence(UUID tenantId, Instant from, Instant to) {
        Double current = jdbcTemplate.queryForObject("""
                SELECT COALESCE(sum(revenue),0)
                FROM aggregates_revenue_hourly
                WHERE tenant_id = ? AND bucket_start >= ? AND bucket_start < ?
                """, Double.class, tenantId, from, to);
        Double previous = jdbcTemplate.queryForObject("""
                SELECT COALESCE(sum(revenue),0)
                FROM aggregates_revenue_hourly
                WHERE tenant_id = ? AND bucket_start >= ? - (? - ?) AND bucket_start < ?
                """, Double.class, tenantId, from, to, from, from);
        double c = current == null ? 0 : current;
        double p = previous == null ? c : previous;
        if (p <= 0) {
            return 50;
        }
        double ratio = Math.min(1.0, Math.max(0, c / p));
        return (int) Math.round(55 + ratio * 35);
    }

    private List<Map<String, Object>> insights(UUID tenantId, Instant from, Instant to) {
        List<Map<String, Object>> insights = new ArrayList<>();

        Double last7 = jdbcTemplate.queryForObject("""
                SELECT COALESCE(sum(revenue),0)
                FROM aggregates_revenue_hourly
                WHERE tenant_id = ? AND bucket_start >= now() - interval '7 day'
                """, Double.class, tenantId);
        Double prev7 = jdbcTemplate.queryForObject("""
                SELECT COALESCE(sum(revenue),0)
                FROM aggregates_revenue_hourly
                WHERE tenant_id = ? AND bucket_start >= now() - interval '14 day' AND bucket_start < now() - interval '7 day'
                """, Double.class, tenantId);

        double l7 = last7 == null ? 0 : last7;
        double p7 = prev7 == null ? 0 : prev7;
        if (p7 > 0 && l7 < p7 * 0.9) {
            insights.add(Map.of("title", "Revenue drop detected", "description", "Revenue dropped more than 10% week over week.", "severity", "high"));
        }

        Integer agingSpike = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM events_raw
                WHERE tenant_id = ?
                  AND received_at >= ?
                  AND received_at < ?
                  AND COALESCE((properties->>'idleDays')::int, 0) > 20
                """, Integer.class, tenantId, from, to);
        if (agingSpike != null && agingSpike > 10) {
            insights.add(Map.of("title", "Deal aging spike", "description", agingSpike + " deals are idle for more than 20 days.", "severity", "medium"));
        }

        long leads = countEvent(tenantId, from, to, "LeadCreated");
        long won = countEvent(tenantId, from, to, "DealWon");
        double conv = leads == 0 ? 0 : won * 1.0 / leads;
        if (conv > 0 && conv < 0.15) {
            insights.add(Map.of("title", "Conversion dip", "description", "Conversion dropped below 15%.", "severity", "medium"));
        }

        if (insights.isEmpty()) {
            insights.add(Map.of("title", "No anomalies", "description", "Key metrics are within expected ranges.", "severity", "info"));
        }
        return insights;
    }

    private long countEvent(UUID tenantId, Instant from, Instant to, String eventName) {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COALESCE(sum(count), 0)
                FROM aggregates_event_counts_day
                WHERE tenant_id = ?
                  AND bucket_start >= ?
                  AND bucket_start < ?
                  AND event_name = ?
                """, Long.class, tenantId, from.atZone(ZoneOffset.UTC).toLocalDate(), to.atZone(ZoneOffset.UTC).toLocalDate(), eventName);
        return value == null ? 0 : value;
    }

    private String currency(double value) {
        return "$" + String.format("%,.0f", value);
    }

    private String scopedOwner(String role, String actor, String owner) {
        if ("rep".equalsIgnoreCase(role) && actor != null && !actor.isBlank()) {
            return actor;
        }
        return nullIfAll(owner);
    }

    private String nullIfAll(String value) {
        if (value == null || value.isBlank() || "All".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }
}
