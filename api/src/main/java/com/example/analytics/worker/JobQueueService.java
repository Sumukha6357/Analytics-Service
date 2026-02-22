package com.example.analytics.worker;

import com.example.analytics.config.AnalyticsProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class JobQueueService {

    private final JdbcTemplate jdbcTemplate;
    private final AnalyticsProperties properties;

    public JobQueueService(JdbcTemplate jdbcTemplate, AnalyticsProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Transactional
    public Optional<JobRecord> acquireNext(String workerId) {
        return jdbcTemplate.query("""
                WITH expired AS (
                    UPDATE job_queue
                    SET status = 'PENDING', locked_at = NULL, locked_by = NULL, updated_at = now()
                    WHERE status = 'RUNNING'
                      AND locked_at < now() - (? * interval '1 second')
                ), next_job AS (
                    SELECT id
                    FROM job_queue
                    WHERE status = 'PENDING' AND not_before <= now()
                    ORDER BY not_before, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                UPDATE job_queue j
                SET status = 'RUNNING', locked_at = now(), locked_by = ?, updated_at = now()
                FROM next_job
                WHERE j.id = next_job.id
                RETURNING j.id, j.tenant_id, j.job_type, j.not_before, j.attempts, j.max_attempts, j.payload::text
                """, ps -> {
            ps.setInt(1, properties.getJobLockTtlSeconds());
            ps.setString(2, workerId);
        }, (rs, rowNum) -> new JobRecord(
                rs.getLong("id"),
                rs.getObject("tenant_id", java.util.UUID.class),
                rs.getString("job_type"),
                rs.getTimestamp("not_before").toInstant(),
                rs.getInt("attempts"),
                rs.getInt("max_attempts"),
                rs.getString("payload")
        )).stream().findFirst();
    }

    public void markDone(long jobId) {
        jdbcTemplate.update("""
                UPDATE job_queue
                SET status = 'DONE', updated_at = now(), locked_at = NULL, locked_by = NULL
                WHERE id = ?
                """, jobId);
    }

    public void markFailed(JobRecord job, Exception ex) {
        int attempts = job.attempts() + 1;
        String status = attempts >= job.maxAttempts() ? "DEAD" : "PENDING";
        long backoff = Math.min(3600L, (long) properties.getJobBackoffBaseSeconds() * (1L << Math.min(12, attempts)));
        Instant nextRun = Instant.now().plusSeconds(backoff);
        jdbcTemplate.update("""
                UPDATE job_queue
                SET status = ?, attempts = ?, not_before = ?, updated_at = now(), locked_at = NULL, locked_by = NULL,
                    payload = jsonb_set(payload, '{lastError}', to_jsonb(?::text), true)
                WHERE id = ?
                """, status, attempts, nextRun, ex.getMessage(), job.id());
    }

    public void enqueueSystemJob(String jobType, String jobKey, String payload, Instant notBefore) {
        jdbcTemplate.update("""
                INSERT INTO job_queue (tenant_id, job_type, job_key, not_before, payload, status, created_at, updated_at)
                VALUES (NULL, ?, ?, ?, ?::jsonb, 'PENDING', now(), now())
                ON CONFLICT (tenant_id, job_type, job_key) WHERE status IN ('PENDING','RUNNING') DO NOTHING
                """, jobType, jobKey, notBefore, payload);
    }

    public long queueDepth() {
        Long value = jdbcTemplate.queryForObject("SELECT count(*) FROM job_queue WHERE status = 'PENDING'", Long.class);
        return value == null ? 0L : value;
    }

    public long oldestPendingAgeSeconds() {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COALESCE(EXTRACT(EPOCH FROM (now() - min(not_before))), 0)
                FROM job_queue
                WHERE status = 'PENDING'
                """, Long.class);
        return value == null ? 0L : value;
    }
}
