package com.example.analytics.security;

import java.util.UUID;

public record AuthPrincipal(UUID tenantId, UUID apiKeyId, String tenantName) {
}
