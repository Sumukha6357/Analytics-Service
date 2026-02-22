package com.example.analytics.worker;

public enum JobType {
    AGG_MINUTE,
    AGG_DAILY,
    RETENTION_CLEANUP,
    PARTITION_MAINT
}
