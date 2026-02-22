package com.example.analytics.service;

import com.example.analytics.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdempotencyServiceTest {

    @Test
    void returnsStoredResponseWhenHashMatches() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        IdempotencyService.StoredResponse stored = new IdempotencyService.StoredResponse("hash", 202, "{\"ok\":true}");

        when(jdbcTemplate.queryForObject(startsWith("SELECT pg_advisory_xact_lock"), eq(Long.class), anyString())).thenReturn(1L);
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any(), any(), any())).thenReturn(stored);

        IdempotencyService service = new IdempotencyService(jdbcTemplate, new ObjectMapper());
        Optional<IdempotencyService.StoredResponse> result = service.findOrValidate(UUID.randomUUID(), "/v1/track", "idem", "hash");

        assertTrue(result.isPresent());
        assertEquals(202, result.get().statusCode());
    }

    @Test
    void throwsConflictWhenHashMismatch() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        IdempotencyService.StoredResponse stored = new IdempotencyService.StoredResponse("hash-a", 202, "{}");

        when(jdbcTemplate.queryForObject(startsWith("SELECT pg_advisory_xact_lock"), eq(Long.class), anyString())).thenReturn(1L);
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any(), any(), any())).thenReturn(stored);

        IdempotencyService service = new IdempotencyService(jdbcTemplate, new ObjectMapper());

        assertThrows(ApiException.class, () -> service.findOrValidate(UUID.randomUUID(), "/v1/track", "idem", "hash-b"));
    }
}
