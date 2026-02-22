package com.example.analytics.service;

import com.example.analytics.config.AnalyticsProperties;
import com.example.analytics.model.EventWriteModel;
import com.example.analytics.model.TrackAcceptedResponse;
import com.example.analytics.model.TrackEventRequest;
import com.example.analytics.repo.EventsRepository;
import com.example.analytics.security.AuthPrincipal;
import com.example.analytics.web.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IngestionService {

    private final EventsRepository eventsRepository;
    private final RateLimitService rateLimitService;
    private final IdempotencyService idempotencyService;
    private final AnalyticsProperties properties;
    private final AuditLogService auditLogService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Counter ingestionRequests;
    private final Counter ingestionEvents;
    private final MeterRegistry meterRegistry;

    public IngestionService(EventsRepository eventsRepository,
                            RateLimitService rateLimitService,
                            IdempotencyService idempotencyService,
                            AnalyticsProperties properties,
                            AuditLogService auditLogService,
                            JdbcTemplate jdbcTemplate,
                            ObjectMapper objectMapper,
                            MeterRegistry meterRegistry) {
        this.eventsRepository = eventsRepository;
        this.rateLimitService = rateLimitService;
        this.idempotencyService = idempotencyService;
        this.properties = properties;
        this.auditLogService = auditLogService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.ingestionRequests = meterRegistry.counter("analytics_ingest_requests_total");
        this.ingestionEvents = meterRegistry.counter("analytics_ingest_events_total");
    }

    @Transactional
    public IngestionResult ingest(AuthPrincipal principal,
                                  String route,
                                  List<TrackEventRequest> requests,
                                  String idempotencyKey,
                                  String requestHash,
                                  String ip,
                                  String userAgent,
                                  String correlationId) {
        ingestionRequests.increment();

        if (requests.isEmpty()) {
            reject(principal, ip, userAgent, correlationId, "empty_batch", 0);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Empty events list");
        }
        if (requests.size() > properties.getMaxBatchSize()) {
            reject(principal, ip, userAgent, correlationId, "batch_limit", requests.size());
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Batch size exceeds max");
        }

        if (!rateLimitService.tryConsume(principal.tenantId(), requests.size())) {
            reject(principal, ip, userAgent, correlationId, "rate_limit", requests.size());
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = idempotencyService.findOrValidate(principal.tenantId(), route, idempotencyKey, requestHash);
            if (existing.isPresent()) {
                return IngestionResult.fromStored(existing.get());
            }
        }

        boolean strictSchema = tenantStrictSchema(principal.tenantId());
        Instant now = Instant.now();
        UUID ingestionId = UUID.randomUUID();
        List<EventWriteModel> rows = new ArrayList<>(requests.size());
        for (TrackEventRequest req : requests) {
            validate(principal.tenantId(), req, strictSchema, now);
            Instant eventTs = req.getTimestamp() == null ? now : req.getTimestamp();
            UUID eventUuid = req.getEventId() == null ? UUID.randomUUID() : req.getEventId();
            rows.add(new EventWriteModel(principal.tenantId(), now, eventTs, req.getEvent().trim(),
                    blankToNull(req.getUserId()), blankToNull(req.getAnonymousId()), blankToNull(req.getSessionId()), eventUuid,
                    req.getSchemaVersion(), req.getProperties(), req.getContext(), blankToNull(idempotencyKey), ingestionId));
        }

        EventsRepository.InsertResult insertResult = eventsRepository.insertEvents(rows);
        for (Instant minuteBucket : insertResult.minuteBuckets()) {
            eventsRepository.enqueueMinuteJob(principal.tenantId(), minuteBucket);
        }
        for (java.time.LocalDate day : insertResult.eventDays()) {
            eventsRepository.enqueueDailyJob(principal.tenantId(), day);
        }

        for (EventWriteModel row : rows) {
            upsertRegistry(row);
        }

        TrackAcceptedResponse response = new TrackAcceptedResponse(
                ingestionId,
                now,
                insertResult.accepted(),
                insertResult.deduped(),
                0
        );

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyService.store(principal.tenantId(), route, idempotencyKey, requestHash, HttpStatus.ACCEPTED.value(), Map.of(
                    "ingestionId", response.ingestionId(),
                    "receivedAt", response.receivedAt().toString(),
                    "accepted", response.accepted(),
                    "deduped", response.deduped(),
                    "rejected", response.rejected()
            ));
        }

        ingestionEvents.increment(insertResult.accepted());
        auditLogService.log(principal.tenantId(), principal.apiKeyId().toString(), "INGESTION_ACCEPTED", true,
                ip, userAgent, correlationId, Map.of(
                        "events", requests.size(),
                        "accepted", insertResult.accepted(),
                        "deduped", insertResult.deduped(),
                        "ingestionId", ingestionId));
        return IngestionResult.accepted(response);
    }

    private void validate(UUID tenantId, TrackEventRequest req, boolean strictSchema, Instant now) {
        if (req.getEvent() == null || req.getEvent().isBlank()) {
            meterRegistry.counter("analytics_ingest_rejected_total", "reason", "event_missing").increment();
            throw new ApiException(HttpStatus.BAD_REQUEST, "event is required");
        }
        if (req.getUserId() == null && req.getAnonymousId() == null) {
            meterRegistry.counter("analytics_ingest_rejected_total", "reason", "user_missing").increment();
            throw new ApiException(HttpStatus.BAD_REQUEST, "Either userId or anonymousId is required");
        }
        if (req.getTimestamp() != null && req.getTimestamp().isAfter(now.plus(24, ChronoUnit.HOURS))) {
            meterRegistry.counter("analytics_ingest_rejected_total", "reason", "future_timestamp").increment();
            throw new ApiException(HttpStatus.BAD_REQUEST, "timestamp too far in future");
        }

        int eventBytes = asBytes(req);
        if (eventBytes > properties.getMaxEventBytes()) {
            meterRegistry.counter("analytics_ingest_rejected_total", "reason", "event_too_large").increment();
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "event payload too large");
        }

        int propertiesBytes = asBytes(req.getProperties() == null ? Map.of() : req.getProperties());
        if (propertiesBytes > properties.getMaxPropertiesBytes()) {
            meterRegistry.counter("analytics_ingest_rejected_total", "reason", "properties_too_large").increment();
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "event properties too large");
        }

        if (strictSchema) {
            Integer latest = jdbcTemplate.query("""
                    SELECT latest_schema_version
                    FROM events_registry
                    WHERE tenant_id = ? AND event_name = ?
                    """, rs -> rs.next() ? rs.getInt(1) : null, tenantId, req.getEvent().trim());
            if (latest != null && req.getSchemaVersion() < latest) {
                meterRegistry.counter("analytics_ingest_rejected_total", "reason", "schema_regression").increment();
                throw new ApiException(HttpStatus.BAD_REQUEST, "schemaVersion cannot decrease in strict mode");
            }
            if (req.getProperties() != null && req.getProperties().size() > 64) {
                meterRegistry.counter("analytics_ingest_rejected_total", "reason", "strict_key_explosion").increment();
                throw new ApiException(HttpStatus.BAD_REQUEST, "strict schema rejects property key explosion");
            }
        }
    }

    private void upsertRegistry(EventWriteModel row) {
        jdbcTemplate.update("""
                INSERT INTO events_registry (tenant_id, event_name, latest_schema_version, first_seen_at, last_seen_at, sample_properties, total_seen)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, 1)
                ON CONFLICT (tenant_id, event_name)
                DO UPDATE SET latest_schema_version = GREATEST(events_registry.latest_schema_version, EXCLUDED.latest_schema_version),
                              last_seen_at = EXCLUDED.last_seen_at,
                              sample_properties = CASE
                                WHEN jsonb_object_length(events_registry.sample_properties) < 50 THEN events_registry.sample_properties || EXCLUDED.sample_properties
                                ELSE events_registry.sample_properties
                              END,
                              total_seen = events_registry.total_seen + 1
                """, row.tenantId(), row.eventName(), row.schemaVersion(), row.receivedAt(), row.receivedAt(), toJson(row.properties()));
    }

    private boolean tenantStrictSchema(UUID tenantId) {
        Boolean strict = jdbcTemplate.queryForObject(
                "SELECT strict_schema FROM tenants WHERE id = ?",
                Boolean.class,
                tenantId);
        return Boolean.TRUE.equals(strict);
    }

    private int asBytes(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value).length;
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid payload JSON");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event JSON", e);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void reject(AuthPrincipal principal, String ip, String userAgent, String correlationId, String reason, int events) {
        meterRegistry.counter("analytics_ingest_rejected_total", "reason", reason).increment();
        auditLogService.log(principal.tenantId(), principal.apiKeyId().toString(), "INGESTION_REJECTED", false,
                ip, userAgent, correlationId, Map.of("reason", reason, "events", events));
    }

    public record IngestionResult(Integer statusCode, String rawBody, TrackAcceptedResponse acceptedResponse) {
        public static IngestionResult accepted(TrackAcceptedResponse response) {
            return new IngestionResult(HttpStatus.ACCEPTED.value(), null, response);
        }

        public static IngestionResult fromStored(IdempotencyService.StoredResponse storedResponse) {
            return new IngestionResult(storedResponse.statusCode(), storedResponse.responseBody(), null);
        }
    }
}
