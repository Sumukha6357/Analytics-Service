package com.example.analytics.service;

import com.example.analytics.security.AuthPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApiKeyService {

    private final JdbcTemplate jdbcTemplate;

    public ApiKeyService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AuthPrincipal> authenticateTenantApiKey(String rawApiKey) {
        String hash = sha256(rawApiKey);
        List<KeyRow> keys = jdbcTemplate.query("""
                SELECT t.id AS tenant_id, t.name AS tenant_name, k.id AS api_key_id, k.key_hash
                FROM tenant_api_keys k
                JOIN tenants t ON t.id = k.tenant_id
                WHERE t.status = 'active'
                  AND k.revoked_at IS NULL
                  AND k.enabled = TRUE
                  AND k.key_hash = ?
                """, (rs, rowNum) -> new KeyRow(
                rs.getObject("tenant_id", UUID.class),
                rs.getString("tenant_name"),
                rs.getObject("api_key_id", UUID.class),
                rs.getString("key_hash")
        ), hash);

        for (KeyRow key : keys) {
            if (MessageDigest.isEqual(key.keyHash().getBytes(StandardCharsets.UTF_8), hash.getBytes(StandardCharsets.UTF_8))) {
                jdbcTemplate.update("UPDATE tenant_api_keys SET last_used_at = now() WHERE id = ?", key.apiKeyId());
                return Optional.of(new AuthPrincipal(key.tenantId(), key.apiKeyId(), key.tenantName()));
            }
        }
        return Optional.empty();
    }

    public String createApiKey(UUID tenantId, String name) {
        String plaintext = "ak_" + UUID.randomUUID() + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String hash = sha256(plaintext);
        jdbcTemplate.update("""
                INSERT INTO tenant_api_keys (id, tenant_id, key_hash, name, enabled)
                VALUES (?, ?, ?, ?, TRUE)
                """, UUID.randomUUID(), tenantId, hash, name);
        return plaintext;
    }

    public void revokeKey(UUID tenantId, UUID keyId) {
        jdbcTemplate.update("""
                UPDATE tenant_api_keys
                SET revoked_at = now(), enabled = FALSE
                WHERE tenant_id = ? AND id = ?
                """, tenantId, keyId);
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private record KeyRow(UUID tenantId, String tenantName, UUID apiKeyId, String keyHash) {
    }
}
