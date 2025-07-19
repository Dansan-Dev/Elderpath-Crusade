package io.github.forest_of_dreams.api;

import lombok.Getter;

public enum BaseURL {
    BACKEND("http://localhost:8080");

    @Getter private final String baseUrl;

    BaseURL(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
