package com.example.analytics.model;

public class UpdateTenantRequest {
    private Integer rateLimitPerMin;
    private Boolean strictSchema;

    public Integer getRateLimitPerMin() {
        return rateLimitPerMin;
    }

    public void setRateLimitPerMin(Integer rateLimitPerMin) {
        this.rateLimitPerMin = rateLimitPerMin;
    }

    public Boolean getStrictSchema() {
        return strictSchema;
    }

    public void setStrictSchema(Boolean strictSchema) {
        this.strictSchema = strictSchema;
    }
}
