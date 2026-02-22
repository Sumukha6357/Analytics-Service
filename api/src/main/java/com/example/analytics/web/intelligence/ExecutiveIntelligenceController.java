package com.example.analytics.web.intelligence;

import com.example.analytics.security.TenantContext;
import com.example.analytics.service.AuditLogService;
import com.example.analytics.service.ExecutiveAnalyticsService;
import com.example.analytics.service.MetricsQueryService;
import com.example.analytics.service.QueryRateLimitService;
import com.example.analytics.web.ApiException;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

@RestController
@RequestMapping("/v1/intelligence")
public class ExecutiveIntelligenceController {

    private final ExecutiveAnalyticsService service;
    private final QueryRateLimitService rateLimitService;
    private final MeterRegistry meterRegistry;
    private final AuditLogService auditLogService;
    private final MetricsQueryService metricsQueryService;

    public ExecutiveIntelligenceController(ExecutiveAnalyticsService service,
                                           QueryRateLimitService rateLimitService,
                                           MeterRegistry meterRegistry,
                                           AuditLogService auditLogService,
                                           MetricsQueryService metricsQueryService) {
        this.service = service;
        this.rateLimitService = rateLimitService;
        this.meterRegistry = meterRegistry;
        this.auditLogService = auditLogService;
        this.metricsQueryService = metricsQueryService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(@RequestParam Instant from,
                                       @RequestParam Instant to,
                                       @RequestParam(defaultValue = "All") String owner,
                                       @RequestParam(defaultValue = "All") String pipeline,
                                       @RequestParam(defaultValue = "All") String segment,
                                       @RequestParam(defaultValue = "admin") String role,
                                       @RequestParam(defaultValue = "") String actor) {
        return guarded(() -> ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.maxAge(Duration.ofSeconds(30)))
                .body(service.dashboard(TenantContext.tenantId(), from, to, owner, pipeline, segment, role, actor)));
    }

    @GetMapping("/reps")
    public ResponseEntity<?> reps(@RequestParam Instant from,
                                  @RequestParam Instant to,
                                  @RequestParam(defaultValue = "All") String owner,
                                  @RequestParam(defaultValue = "admin") String role,
                                  @RequestParam(defaultValue = "") String actor,
                                  @RequestParam(defaultValue = "10") int limit,
                                  @RequestParam(defaultValue = "0") int offset) {
        return guarded(() -> ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.maxAge(Duration.ofSeconds(20)))
                .body(service.reps(TenantContext.tenantId(), from, to, owner, role, actor, safeLimit(limit), safeOffset(offset))));
    }

    @GetMapping("/deals")
    public ResponseEntity<?> deals(@RequestParam Instant from,
                                   @RequestParam Instant to,
                                   @RequestParam(defaultValue = "high") String kind,
                                   @RequestParam(defaultValue = "All") String owner,
                                   @RequestParam(defaultValue = "All") String pipeline,
                                   @RequestParam(defaultValue = "All") String segment,
                                   @RequestParam(defaultValue = "admin") String role,
                                   @RequestParam(defaultValue = "") String actor,
                                   @RequestParam(defaultValue = "10") int limit,
                                   @RequestParam(defaultValue = "0") int offset) {
        return guarded(() -> ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.maxAge(Duration.ofSeconds(20)))
                .body(service.deals(TenantContext.tenantId(), from, to, kind, owner, pipeline, segment, role, actor, safeLimit(limit), safeOffset(offset))));
    }

    @GetMapping("/alerts")
    public ResponseEntity<?> alerts(@RequestParam Instant from,
                                    @RequestParam Instant to) {
        return guarded(() -> ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.maxAge(Duration.ofSeconds(20)))
                .body(service.alerts(TenantContext.tenantId(), from, to)));
    }

    @GetMapping("/system/status")
    public ResponseEntity<?> systemStatus() {
        return guarded(() -> ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.maxAge(Duration.ofSeconds(10)))
                .body(metricsQueryService.systemStatus()));
    }

    @PostMapping("/share")
    public ResponseEntity<?> createShare(@RequestBody(required = false) Map<String, Object> payload,
                                         @RequestParam(defaultValue = "60") int expiresMinutes) {
        return guarded(() -> ResponseEntity.ok(service.createShare(TenantContext.tenantId(), payload == null ? Map.of() : payload, expiresMinutes)));
    }

    @GetMapping("/share/{token}")
    public ResponseEntity<?> resolveShare(@PathVariable UUID token) {
        return guarded(() -> {
            Map<String, Object> resolved = service.resolveShare(TenantContext.tenantId(), token);
            if (resolved.isEmpty()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Share token invalid or expired");
            }
            return ResponseEntity.ok(resolved);
        });
    }

    @PostMapping("/export")
    public ResponseEntity<?> createExport(@RequestParam Instant from,
                                          @RequestParam Instant to,
                                          @RequestParam(defaultValue = "All") String owner,
                                          @RequestParam(defaultValue = "All") String pipeline,
                                          @RequestParam(defaultValue = "All") String segment,
                                          @RequestParam(defaultValue = "admin") String role,
                                          @RequestParam(defaultValue = "") String actor,
                                          @RequestParam(defaultValue = "csv") String format) {
        return guarded(() -> {
            Map<String, Object> export = service.createExport(TenantContext.tenantId(), format,
                    TenantContext.getRequired().apiKeyId().toString(), from, to, owner, pipeline, segment, role, actor);
            auditLogService.log(TenantContext.tenantId(), TenantContext.getRequired().apiKeyId().toString(), "REPORT_EXPORTED", true,
                    null, null, org.slf4j.MDC.get("correlationId"), Map.of("format", format, "token", export.get("token")));
            return ResponseEntity.ok(export);
        });
    }

    @GetMapping("/export/{token}")
    public ResponseEntity<?> downloadExport(@PathVariable UUID token) {
        return guarded(() -> {
            Map<String, Object> export = service.resolveExport(TenantContext.tenantId(), token);
            if (export.isEmpty()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Export token invalid or expired");
            }
            String content = String.valueOf(export.get("content"));
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=executive-report.csv")
                    .body(content);
        });
    }

    private ResponseEntity<?> guarded(Callable<ResponseEntity<?>> callable) {
        long start = System.currentTimeMillis();
        try {
            UUID tenantId = TenantContext.tenantId();
            if (!rateLimitService.allow(tenantId, 180)) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Analytics query rate limit exceeded");
            }
            return callable.call();
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            meterRegistry.timer("analytics_query_duration_ms").record(System.currentTimeMillis() - start, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    private int safeLimit(int limit) {
        return Math.min(200, Math.max(1, limit));
    }

    private int safeOffset(int offset) {
        return Math.max(0, offset);
    }
}
