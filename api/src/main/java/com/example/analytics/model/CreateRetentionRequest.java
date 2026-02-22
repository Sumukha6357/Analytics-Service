package com.example.analytics.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class CreateRetentionRequest {
    @NotBlank
    private String tenantId;
    @NotBlank
    private String name;
    @NotBlank
    private String cohortEvent;
    @NotBlank
    private String returnEvent;
    @Min(1)
    private int windowDays;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCohortEvent() {
        return cohortEvent;
    }

    public void setCohortEvent(String cohortEvent) {
        this.cohortEvent = cohortEvent;
    }

    public String getReturnEvent() {
        return returnEvent;
    }

    public void setReturnEvent(String returnEvent) {
        this.returnEvent = returnEvent;
    }

    public int getWindowDays() {
        return windowDays;
    }

    public void setWindowDays(int windowDays) {
        this.windowDays = windowDays;
    }
}
