package com.example.analytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditLogService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditLogService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void log(UUID tenantId,
                    String actor,
                    String eventType,
                    boolean success,
                    String ip,
                    String userAgent,
                    String correlationId,
                    Map<String, Object> details) {
        jdbcTemplate.update("""
                INSERT INTO audit_logs (id, tenant_id, actor, event_type, success, ip, user_agent, correlation_id, details)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """, UUID.randomUUID(), tenantId, actor, eventType, success, ip, userAgent, correlationId, toJson(details));
    }

    private String toJson(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details == null ? Map.of() : details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audit details", e);
        }
    }
}
