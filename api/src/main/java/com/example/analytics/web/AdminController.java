package com.example.analytics.web;

import com.example.analytics.model.CreateApiKeyRequest;
import com.example.analytics.model.CreateFunnelRequest;
import com.example.analytics.model.CreateRetentionRequest;
import com.example.analytics.model.CreateTenantRequest;
import com.example.analytics.model.RunJobRequest;
import com.example.analytics.model.UpdateTenantRequest;
import com.example.analytics.service.AdminService;
import com.example.analytics.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;
    private final AuditLogService auditLogService;

    public AdminController(AdminService adminService, AuditLogService auditLogService) {
        this.adminService = adminService;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/tenants")
    public ResponseEntity<?> createTenant(@Valid @RequestBody CreateTenantRequest request, HttpServletRequest httpRequest) {
        UUID tenantId = adminService.createTenant(request.getName());
        auditLogService.log(tenantId, "admin", "TENANT_CREATED", true,
                httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"), MDC.get(CorrelationIdFilter.MDC_KEY),
                Map.of("tenantId", tenantId, "name", request.getName()));
        return ResponseEntity.ok(Map.of("tenantId", tenantId));
    }

    @PatchMapping("/tenants/{tenantId}")
    public ResponseEntity<?> updateTenant(@PathVariable UUID tenantId,
                                          @RequestBody UpdateTenantRequest request,
                                          HttpServletRequest httpRequest) {
        adminService.updateTenant(tenantId, request);
        auditLogService.log(tenantId, "admin", "TENANT_UPDATED", true,
                httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"), MDC.get(CorrelationIdFilter.MDC_KEY),
                Map.of("rateLimitPerMin", request.getRateLimitPerMin(), "strictSchema", request.getStrictSchema()));
        return ResponseEntity.ok(Map.of("updated", true));
    }

    @PostMapping("/tenants/{tenantId}/keys")
    public ResponseEntity<?> createApiKey(@PathVariable UUID tenantId,
                                          @Valid @RequestBody CreateApiKeyRequest request,
                                          HttpServletRequest httpRequest) {
        String apiKey = adminService.createApiKey(tenantId, request.getName());
        auditLogService.log(tenantId, "admin", "API_KEY_CREATED", true,
                httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"), MDC.get(CorrelationIdFilter.MDC_KEY),
                Map.of("name", request.getName()));
        return ResponseEntity.ok(Map.of("apiKey", apiKey));
    }

    @PostMapping("/tenants/{tenantId}/keys/{keyId}/revoke")
    public ResponseEntity<?> revokeApiKey(@PathVariable UUID tenantId,
                                          @PathVariable UUID keyId,
                                          HttpServletRequest httpRequest) {
        adminService.revokeApiKey(tenantId, keyId);
        auditLogService.log(tenantId, "admin", "API_KEY_REVOKED", true,
                httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"), MDC.get(CorrelationIdFilter.MDC_KEY),
                Map.of("keyId", keyId));
        return ResponseEntity.ok(Map.of("revoked", true));
    }

    @GetMapping("/tenants/{tenantId}/keys")
    public ResponseEntity<?> listKeys(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(adminService.listApiKeys(tenantId));
    }

    @PostMapping("/jobs/run")
    public ResponseEntity<?> runJob(@RequestBody(required = false) RunJobRequest request,
                                    HttpServletRequest httpRequest) {
        String jobType = request == null ? null : request.getJobType();
        long jobId = adminService.runJob(jobType);
        auditLogService.log(null, "admin", "JOB_RUN_REQUESTED", true,
                httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"), MDC.get(CorrelationIdFilter.MDC_KEY),
                Map.of("jobType", jobType, "jobId", jobId));
        return ResponseEntity.ok(Map.of("jobId", jobId));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<?> auditLogs(@RequestParam(defaultValue = "100") int limit,
                                       @RequestParam(defaultValue = "0") int offset) {
        int safeLimit = Math.min(500, Math.max(1, limit));
        int safeOffset = Math.max(0, offset);
        return ResponseEntity.ok(adminService.auditLogs(safeLimit, safeOffset));
    }

    @PostMapping("/funnels")
    public ResponseEntity<?> createFunnel(@Valid @RequestBody CreateFunnelRequest request, HttpServletRequest httpRequest) {
        UUID funnelId = adminService.createFunnel(request);
        UUID tenantId = UUID.fromString(request.getTenantId());
        auditLogService.log(tenantId, "admin", "FUNNEL_CREATED", true,
                httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"), MDC.get(CorrelationIdFilter.MDC_KEY),
                Map.of("funnelId", funnelId));
        return ResponseEntity.ok(Map.of("funnelId", funnelId));
    }

    @PostMapping("/retention")
    public ResponseEntity<?> createRetention(@Valid @RequestBody CreateRetentionRequest request, HttpServletRequest httpRequest) {
        UUID retentionId = adminService.createRetention(request);
        UUID tenantId = UUID.fromString(request.getTenantId());
        auditLogService.log(tenantId, "admin", "RETENTION_CREATED", true,
                httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"), MDC.get(CorrelationIdFilter.MDC_KEY),
                Map.of("retentionId", retentionId));
        return ResponseEntity.ok(Map.of("retentionId", retentionId));
    }
}
