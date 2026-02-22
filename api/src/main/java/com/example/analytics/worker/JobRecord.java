package com.example.analytics.worker;

import java.time.Instant;
import java.util.UUID;

public record JobRecord(long id,
                        UUID tenantId,
                        String jobType,
                        Instant notBefore,
                        int attempts,
                        int maxAttempts,
                        String payload) {
}
