package com.example.analytics.security;

import java.util.UUID;

public final class TenantContext {
    private static final ThreadLocal<AuthPrincipal> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(AuthPrincipal principal) {
        CURRENT.set(principal);
    }

    public static AuthPrincipal getRequired() {
        AuthPrincipal principal = CURRENT.get();
        if (principal == null) {
            throw new IllegalStateException("No tenant principal in context");
        }
        return principal;
    }

    public static UUID tenantId() {
        return getRequired().tenantId();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
