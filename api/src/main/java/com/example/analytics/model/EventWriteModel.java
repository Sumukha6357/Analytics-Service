package com.example.analytics.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EventWriteModel(
        UUID tenantId,
        Instant receivedAt,
        Instant eventTs,
        String eventName,
        String userId,
        String anonymousId,
        String sessionId,
        UUID eventUuid,
        int schemaVersion,
        Map<String, Object> properties,
        Map<String, Object> context,
        String idempotencyKey,
        UUID ingestionId
) {
}
