package com.example.analytics.model;

import java.time.Instant;
import java.util.UUID;

public record TrackAcceptedResponse(UUID ingestionId,
                                    Instant receivedAt,
                                    int accepted,
                                    int deduped,
                                    int rejected) {
}
