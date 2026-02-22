package com.example.analytics.model;

import jakarta.validation.constraints.NotBlank;

public class CreateApiKeyRequest {
    @NotBlank
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
