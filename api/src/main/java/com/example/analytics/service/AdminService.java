package com.example.analytics.service;

import com.example.analytics.model.CreateFunnelRequest;
import com.example.analytics.model.CreateRetentionRequest;
import com.example.analytics.model.UpdateTenantRequest;
import com.example.analytics.worker.JobType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminService {

    private final JdbcTemplate jdbcTemplate;
    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    public AdminService(JdbcTemplate jdbcTemplate, ApiKeyService apiKeyService, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.apiKeyService = apiKeyService;
        this.objectMapper = objectMapper;
    }

    public UUID createTenant(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tenants (id, name, status, rate_limit_per_min, strict_schema) VALUES (?, ?, 'active', 6000, FALSE)", id, name);
        return id;
    }

    public String createApiKey(UUID tenantId, String name) {
        return apiKeyService.createApiKey(tenantId, name);
    }

    public void revokeApiKey(UUID tenantId, UUID keyId) {
        apiKeyService.revokeKey(tenantId, keyId);
    }

    public List<Map<String, Object>> listApiKeys(UUID tenantId) {
        return jdbcTemplate.queryForList("""
                SELECT id, name, enabled, created_at, last_used_at, revoked_at
                FROM tenant_api_keys
                WHERE tenant_id = ?
                ORDER BY created_at DESC
                """, tenantId);
    }

    public void updateTenant(UUID tenantId, UpdateTenantRequest request) {
        jdbcTemplate.update("""
                UPDATE tenants
                SET rate_limit_per_min = COALESCE(?, rate_limit_per_min),
                    strict_schema = COALESCE(?, strict_schema)
                WHERE id = ?
                """, request.getRateLimitPerMin(), request.getStrictSchema(), tenantId);
    }

    public long runJob(String jobType) {
        String normalized = jobType == null ? JobType.PARTITION_MAINT.name() : jobType;
        Instant now = Instant.now();
        return jdbcTemplate.queryForObject("""
                INSERT INTO job_queue (tenant_id, job_type, job_key, payload, status, not_before, created_at, updated_at)
                VALUES (NULL, ?, ?, '{}'::jsonb, 'PENDING', ?, now(), now())
                RETURNING id
                """, Long.class, normalized, "admin:" + normalized + ":" + now.toString(), now);
    }

    public List<Map<String, Object>> auditLogs(int limit, int offset) {
        return jdbcTemplate.queryForList("""
                SELECT id, tenant_id, actor, event_type, success, correlation_id, details, created_at
                FROM audit_logs
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """, limit, offset);
    }

    public UUID createFunnel(CreateFunnelRequest request) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO funnels_definitions (id, tenant_id, name, steps)
                VALUES (?, ?, ?, ?::jsonb)
                """, id, UUID.fromString(request.getTenantId()), request.getName(), toJson(request.getSteps()));
        return id;
    }

    public UUID createRetention(CreateRetentionRequest request) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO retention_definitions (id, tenant_id, name, cohort_event, return_event, window_days)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, UUID.fromString(request.getTenantId()), request.getName(), request.getCohortEvent(), request.getReturnEvent(), request.getWindowDays());
        return id;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
