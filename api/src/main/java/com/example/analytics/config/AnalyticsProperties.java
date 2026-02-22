package com.example.analytics.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "analytics")
public class AnalyticsProperties {

    @Min(1)
    private int retentionRawDays = 30;
    @Min(1)
    private int rateLimitEventsPerMin = 6000;
    @Min(1)
    private int maxBatchSize = 100;
    @Min(1)
    private int maxEventBytes = 65536;
    @Min(1)
    private int maxPropertiesBytes = 49152;
    @Min(1)
    private int workerConcurrency = 4;
    @Min(1)
    private int partitionPrecreateDays = 7;
    @Min(1)
    private int jobLockTtlSeconds = 120;
    @Min(1)
    private int jobBackoffBaseSeconds = 2;
    @NotBlank
    private String adminApiKey = "change-me";
    @NotBlank
    private String revenueEventName = "OrderPlaced";
    private boolean enableGinProperties = false;

    public int getRetentionRawDays() {
        return retentionRawDays;
    }

    public void setRetentionRawDays(int retentionRawDays) {
        this.retentionRawDays = retentionRawDays;
    }

    public int getRateLimitEventsPerMin() {
        return rateLimitEventsPerMin;
    }

    public void setRateLimitEventsPerMin(int rateLimitEventsPerMin) {
        this.rateLimitEventsPerMin = rateLimitEventsPerMin;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public void setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }

    public int getMaxEventBytes() {
        return maxEventBytes;
    }

    public void setMaxEventBytes(int maxEventBytes) {
        this.maxEventBytes = maxEventBytes;
    }

    public int getMaxPropertiesBytes() {
        return maxPropertiesBytes;
    }

    public void setMaxPropertiesBytes(int maxPropertiesBytes) {
        this.maxPropertiesBytes = maxPropertiesBytes;
    }

    public int getWorkerConcurrency() {
        return workerConcurrency;
    }

    public void setWorkerConcurrency(int workerConcurrency) {
        this.workerConcurrency = workerConcurrency;
    }

    public int getPartitionPrecreateDays() {
        return partitionPrecreateDays;
    }

    public void setPartitionPrecreateDays(int partitionPrecreateDays) {
        this.partitionPrecreateDays = partitionPrecreateDays;
    }

    public int getJobLockTtlSeconds() {
        return jobLockTtlSeconds;
    }

    public void setJobLockTtlSeconds(int jobLockTtlSeconds) {
        this.jobLockTtlSeconds = jobLockTtlSeconds;
    }

    public int getJobBackoffBaseSeconds() {
        return jobBackoffBaseSeconds;
    }

    public void setJobBackoffBaseSeconds(int jobBackoffBaseSeconds) {
        this.jobBackoffBaseSeconds = jobBackoffBaseSeconds;
    }

    public String getAdminApiKey() {
        return adminApiKey;
    }

    public void setAdminApiKey(String adminApiKey) {
        this.adminApiKey = adminApiKey;
    }

    public String getRevenueEventName() {
        return revenueEventName;
    }

    public void setRevenueEventName(String revenueEventName) {
        this.revenueEventName = revenueEventName;
    }

    public boolean isEnableGinProperties() {
        return enableGinProperties;
    }

    public void setEnableGinProperties(boolean enableGinProperties) {
        this.enableGinProperties = enableGinProperties;
    }
}
