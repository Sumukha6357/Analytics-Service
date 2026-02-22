package com.example.analytics.repo;

import com.example.analytics.model.EventWriteModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
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
        int[] result = jdbcTemplate.batchUpdate("""
                INSERT INTO events_raw
                (tenant_id, received_at, event_ts, event_name, user_id, anonymous_id, session_id, event_uuid,
                 schema_version, properties, context, idempotency_key, ingestion_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
                ON CONFLICT (tenant_id, event_uuid) WHERE event_uuid IS NOT NULL DO NOTHING
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                EventWriteModel e = rows[i];
                ps.setObject(1, e.tenantId());
                ps.setTimestamp(2, Timestamp.from(e.receivedAt()));
                ps.setTimestamp(3, Timestamp.from(e.eventTs()));
                ps.setString(4, e.eventName());
                ps.setString(5, e.userId());
                ps.setString(6, e.anonymousId());
                ps.setString(7, e.sessionId());
                ps.setObject(8, e.eventUuid());
                ps.setInt(9, e.schemaVersion());
                ps.setString(10, toJson(e.properties()));
                ps.setString(11, toJson(e.context()));
                ps.setString(12, e.idempotencyKey());
                ps.setObject(13, e.ingestionId());
            }

            @Override
            public int getBatchSize() {
                return rows.length;
            }
        });

        int accepted = 0;
        Set<Instant> minuteBuckets = new LinkedHashSet<>();
        Set<LocalDate> days = new LinkedHashSet<>();
        for (int i = 0; i < result.length; i++) {
            if (result[i] > 0) {
                accepted++;
                minuteBuckets.add(rows[i].receivedAt().truncatedTo(java.time.temporal.ChronoUnit.MINUTES));
                days.add(rows[i].eventTs().atZone(ZoneOffset.UTC).toLocalDate());
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
