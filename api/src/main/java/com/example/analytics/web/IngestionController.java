package com.example.analytics.web;

import com.example.analytics.model.BatchTrackRequest;
import com.example.analytics.model.TrackEventRequest;
import com.example.analytics.security.TenantContext;
import com.example.analytics.service.IdempotencyService;
import com.example.analytics.service.IngestionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/v1")
@Validated
public class IngestionController {

    private final IngestionService ingestionService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public IngestionController(IngestionService ingestionService,
                               IdempotencyService idempotencyService,
                               ObjectMapper objectMapper) {
        this.ingestionService = ingestionService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/track")
    public ResponseEntity<?> track(@Valid @RequestBody TrackEventRequest request,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                   HttpServletRequest httpServletRequest) {
        String rawBody = toJson(request);
        var result = ingestionService.ingest(TenantContext.getRequired(), "/v1/track", List.of(request), idempotencyKey,
                idempotencyService.buildRequestHash("POST", "/v1/track", rawBody),
                httpServletRequest.getRemoteAddr(), httpServletRequest.getHeader("User-Agent"), MDC.get(CorrelationIdFilter.MDC_KEY));
        return mapResponse(result);
    }

    @PostMapping("/batch")
    public ResponseEntity<?> batch(@Valid @RequestBody BatchTrackRequest request,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                   HttpServletRequest httpServletRequest) {
        String rawBody = toJson(request);
        var result = ingestionService.ingest(TenantContext.getRequired(), "/v1/batch", request.getEvents(), idempotencyKey,
                idempotencyService.buildRequestHash("POST", "/v1/batch", rawBody),
                httpServletRequest.getRemoteAddr(), httpServletRequest.getHeader("User-Agent"), MDC.get(CorrelationIdFilter.MDC_KEY));
        return mapResponse(result);
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(java.util.Map.of("status", "UP"));
    }

    private ResponseEntity<?> mapResponse(IngestionService.IngestionResult result) {
        if (result.rawBody() != null) {
            try {
                JsonNode jsonNode = objectMapper.readTree(result.rawBody());
                return ResponseEntity.status(result.statusCode()).body(jsonNode);
            } catch (IOException e) {
                return ResponseEntity.status(result.statusCode()).body(result.rawBody());
            }
        }
        return ResponseEntity.status(result.statusCode()).body(result.acceptedResponse());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize request", e);
        }
    }
}
