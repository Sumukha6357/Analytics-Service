package com.example.analytics.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

public class CreateFunnelRequest {
    @NotBlank
    private String tenantId;
    @NotBlank
    private String name;
    @NotEmpty
    private List<Map<String, Object>> steps;

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

    public List<Map<String, Object>> getSteps() {
        return steps;
    }

    public void setSteps(List<Map<String, Object>> steps) {
        this.steps = steps;
    }
}
