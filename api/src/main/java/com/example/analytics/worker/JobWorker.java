package com.example.analytics.worker;

import com.example.analytics.config.AnalyticsProperties;
import com.example.analytics.service.AggregationService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class JobWorker {

    private final JobQueueService jobQueueService;
    private final AggregationService aggregationService;
    private final ExecutorService executorService;
    private final MeterRegistry meterRegistry;
    private final int workerConcurrency;

    public JobWorker(JobQueueService jobQueueService,
                     AggregationService aggregationService,
                     AnalyticsProperties properties,
                     MeterRegistry meterRegistry) {
        this.jobQueueService = jobQueueService;
        this.aggregationService = aggregationService;
        this.meterRegistry = meterRegistry;
        this.workerConcurrency = properties.getWorkerConcurrency();
        this.executorService = Executors.newFixedThreadPool(workerConcurrency);
    }

    @Scheduled(fixedDelayString = "${analytics.worker-poll-ms:1500}")
    public void poll() {
        for (int i = 0; i < workerConcurrency; i++) {
            executorService.submit(this::processOne);
        }
        meterRegistry.gauge("analytics_jobs_queue_depth", jobQueueService, JobQueueService::queueDepth);
        meterRegistry.gauge("analytics_job_lag_seconds", jobQueueService, JobQueueService::oldestPendingAgeSeconds);
    }

    @Scheduled(fixedDelayString = "${analytics.system-job-poll-ms:60000}")
    public void enqueueSystemJobs() {
        jobQueueService.enqueueSystemJob(JobType.PARTITION_MAINT.name(), "partition:" + LocalDate.now(), "{}", Instant.now());
        jobQueueService.enqueueSystemJob(JobType.RETENTION_CLEANUP.name(), "retention:" + LocalDate.now(), "{}", Instant.now());
    }

    private void processOne() {
        String workerId = UUID.randomUUID().toString();
        jobQueueService.acquireNext(workerId).ifPresent(job -> {
            try {
                JobType type = JobType.valueOf(job.jobType());
                switch (type) {
                    case AGG_MINUTE -> aggregationService.processMinuteJob(job.tenantId(), job.payload());
                    case AGG_DAILY -> aggregationService.processDailyJob(job.tenantId(), job.payload());
                    case RETENTION_CLEANUP -> aggregationService.retentionCleanup();
                    case PARTITION_MAINT -> aggregationService.maintainPartitions();
                    default -> throw new IllegalStateException("Unsupported job type " + job.jobType());
                }
                meterRegistry.counter("analytics_jobs_processed_total", "type", job.jobType(), "status", "done").increment();
                meterRegistry.timer("analytics_job_lag_seconds", "type", job.jobType())
                        .record(Duration.between(job.notBefore(), Instant.now()));
                jobQueueService.markDone(job.id());
            } catch (Exception ex) {
                meterRegistry.counter("analytics_jobs_processed_total", "type", job.jobType(), "status", "failed").increment();
                jobQueueService.markFailed(job, ex);
            }
        });
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);
    }
}
