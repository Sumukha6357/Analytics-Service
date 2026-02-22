package com.example.analytics.web;

import com.example.analytics.security.TenantContext;
import com.example.analytics.service.MetricsQueryService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/v1")
public class MetricsController {

    private final MetricsQueryService queryService;
    private final MeterRegistry meterRegistry;

    public MetricsController(MetricsQueryService queryService, MeterRegistry meterRegistry) {
        this.queryService = queryService;
        this.meterRegistry = meterRegistry;
    }

    @GetMapping("/metrics/event-counts")
    public ResponseEntity<?> eventCounts(@RequestParam Instant from,
                                         @RequestParam Instant to,
                                         @RequestParam(defaultValue = "minute") String interval,
                                         @RequestParam String event) {
        return timed(() -> ResponseEntity.ok(queryService.eventCounts(TenantContext.tenantId(), from, to, interval, event)));
    }

    @GetMapping("/metrics/dau")
    public ResponseEntity<?> dau(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return timed(() -> ResponseEntity.ok(queryService.dau(TenantContext.tenantId(), from, to)));
    }

    @GetMapping("/metrics/revenue")
    public ResponseEntity<?> revenue(@RequestParam Instant from,
                                     @RequestParam Instant to,
                                     @RequestParam(defaultValue = "hour") String interval,
                                     @RequestParam(defaultValue = "USD") String currency) {
        return timed(() -> ResponseEntity.ok(queryService.revenue(TenantContext.tenantId(), from, to, interval, currency)));
    }

    @GetMapping("/funnels/{funnelId}")
    public ResponseEntity<?> funnel(@PathVariable UUID funnelId,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return timed(() -> ResponseEntity.ok(queryService.funnel(TenantContext.tenantId(), funnelId, from, to)));
    }

    @GetMapping("/retention/{retentionId}")
    public ResponseEntity<?> retention(@PathVariable UUID retentionId,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate cohortFrom,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate cohortTo) {
        return timed(() -> ResponseEntity.ok(queryService.retention(TenantContext.tenantId(), retentionId, cohortFrom, cohortTo)));
    }

    @GetMapping("/events")
    public ResponseEntity<?> events(@RequestParam(defaultValue = "50") int limit,
                                    @RequestParam(defaultValue = "0") int offset) {
        int safeLimit = Math.min(Math.max(1, limit), 200);
        int safeOffset = Math.max(0, offset);
        return timed(() -> ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS))
                .body(queryService.listEvents(TenantContext.tenantId(), safeLimit, safeOffset)));
    }

    @GetMapping("/events/{eventName}")
    public ResponseEntity<?> eventDetail(@PathVariable String eventName) {
        return timed(() -> ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS))
                .body(queryService.eventDetail(TenantContext.tenantId(), eventName)));
    }

    @GetMapping("/events/schema")
    public ResponseEntity<?> eventSchema(@RequestParam(defaultValue = "50") int limit,
                                         @RequestParam(defaultValue = "0") int offset) {
        return events(limit, offset);
    }

    @GetMapping("/system/status")
    public ResponseEntity<?> systemStatus() {
        return timed(() -> ResponseEntity.ok(queryService.systemStatus()));
    }

    private ResponseEntity<?> timed(Callable<ResponseEntity<?>> callable) {
        long start = System.currentTimeMillis();
        try {
            return callable.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            meterRegistry.timer("analytics_query_latency_ms")
                    .record(System.currentTimeMillis() - start, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }
}
