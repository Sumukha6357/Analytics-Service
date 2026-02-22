package com.example.analytics.repo;

import com.example.analytics.model.EventWriteModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Repository
public class EventsRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public EventsRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public InsertResult insertEvents(Collection<EventWriteModel> events) {
        EventWriteModel[] rows = events.toArray(EventWriteModel[]::new);
        int accepted = 0;
        Set<Instant> minuteBuckets = new LinkedHashSet<>();
        Set<LocalDate> days = new LinkedHashSet<>();

        for (EventWriteModel row : rows) {
            int dedupClaim = jdbcTemplate.update("""
                    INSERT INTO event_dedup_keys (tenant_id, event_uuid, first_seen_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT (tenant_id, event_uuid) DO NOTHING
                    """, row.tenantId(), row.eventUuid(), Timestamp.from(row.receivedAt()));
            if (dedupClaim == 0) {
                continue;
            }

            int inserted = jdbcTemplate.update("""
                INSERT INTO events_raw
                (tenant_id, received_at, event_ts, event_name, user_id, anonymous_id, session_id, event_uuid,
                 schema_version, properties, context, idempotency_key, ingestion_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
                """,
                    row.tenantId(),
                    Timestamp.from(row.receivedAt()),
                    Timestamp.from(row.eventTs()),
                    row.eventName(),
                    row.userId(),
                    row.anonymousId(),
                    row.sessionId(),
                    row.eventUuid(),
                    row.schemaVersion(),
                    toJson(row.properties()),
                    toJson(row.context()),
                    row.idempotencyKey(),
                    row.ingestionId());

            if (inserted > 0) {
                accepted++;
                minuteBuckets.add(row.receivedAt().truncatedTo(java.time.temporal.ChronoUnit.MINUTES));
                days.add(row.eventTs().atZone(ZoneOffset.UTC).toLocalDate());
            }
        }

        return new InsertResult(accepted, rows.length - accepted, minuteBuckets, days);
    }

    public void enqueueMinuteJob(UUID tenantId, Instant bucketStart) {
        String payload = "{\"bucketStart\":\"" + bucketStart + "\"}";
        jdbcTemplate.update("""
                INSERT INTO job_queue (tenant_id, job_type, job_key, payload, status, not_before, created_at, updated_at)
                VALUES (?, 'AGG_MINUTE', ?, ?::jsonb, 'PENDING', now(), now(), now())
                ON CONFLICT (tenant_id, job_type, job_key) WHERE status IN ('PENDING','RUNNING') DO NOTHING
                """, tenantId, bucketStart.toString(), payload);
    }

    public void enqueueDailyJob(UUID tenantId, LocalDate day) {
        String payload = "{\"dayStart\":\"" + day.atStartOfDay().toInstant(ZoneOffset.UTC) + "\"}";
        jdbcTemplate.update("""
                INSERT INTO job_queue (tenant_id, job_type, job_key, payload, status, not_before, created_at, updated_at)
                VALUES (?, 'AGG_DAILY', ?, ?::jsonb, 'PENDING', now(), now(), now())
                ON CONFLICT (tenant_id, job_type, job_key) WHERE status IN ('PENDING','RUNNING') DO NOTHING
                """, tenantId, day.toString(), payload);
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event JSON", e);
        }
    }

    public record InsertResult(int accepted, int deduped, Set<Instant> minuteBuckets, Set<LocalDate> eventDays) {
    }
}
