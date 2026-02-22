package com.example.analytics.service;

import com.example.analytics.web.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public IdempotencyService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<StoredResponse> findOrValidate(UUID tenantId, String route, String key, String requestHash) {
        lock(tenantId, route, key);
        try {
            StoredResponse stored = jdbcTemplate.queryForObject("""
                    SELECT request_hash, response_code, response_body::text
                    FROM ingestion_idempotency
                    WHERE tenant_id = ? AND route = ? AND idempotency_key = ?
                    """, (rs, rowNum) -> new StoredResponse(
                    rs.getString("request_hash"),
                    rs.getInt("response_code"),
                    rs.getString("response_body")
            ), tenantId, route, key);

            if (!stored.requestHash().equals(requestHash)) {
                throw new ApiException(HttpStatus.CONFLICT, "Idempotency key reused with different request");
            }
            return Optional.of(stored);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public void store(UUID tenantId, String route, String key, String requestHash, int statusCode, Map<String, Object> responseBody) {
        jdbcTemplate.update("""
                INSERT INTO ingestion_idempotency (tenant_id, route, idempotency_key, request_hash, response_code, response_body)
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (tenant_id, route, idempotency_key) DO NOTHING
                """, tenantId, route, key, requestHash, statusCode, toJson(responseBody));
    }

    public String buildRequestHash(String method, String path, String body) {
        return sha256(method + "|" + path + "|" + body);
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void lock(UUID tenantId, String route, String key) {
        jdbcTemplate.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))", Long.class, tenantId + ":" + route + ":" + key);
    }

    private String toJson(Map<String, Object> obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }

    public record StoredResponse(String requestHash, int statusCode, String responseBody) {
    }
}
