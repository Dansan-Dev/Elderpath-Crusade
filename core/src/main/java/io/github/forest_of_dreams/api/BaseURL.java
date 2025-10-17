package io.github.forest_of_dreams.api;

import lombok.Getter;

public enum BaseURL {
    BACKEND("BACKEND_URL", "http://localhost:8080");

    @Getter
    private final String baseUrl;

    BaseURL(String envVarName, String defaultUrl) {
        this.baseUrl = resolveBaseUrl(envVarName, defaultUrl);
    }

    private static String resolveBaseUrl(String envVarName, String defaultUrl) {
        String value = System.getenv(envVarName);
        if (value == null || value.isBlank()) value = defaultUrl;
        return value;
    }
}
