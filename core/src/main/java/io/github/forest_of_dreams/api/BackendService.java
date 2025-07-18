package io.github.forest_of_dreams.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.forest_of_dreams.api.dto.UserIdDto;
import io.github.forest_of_dreams.api.dto.UserResponseDto;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;



public class BackendService {
    private static final String BASE_URL = "http://localhost:8080";
    private static final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private static final ObjectMapper mapper = new ObjectMapper();


    public static <T> CompletableFuture<T> get(String endpoint, Class<T> responseType) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + endpoint))
            .header("Content-Type", "application/json")
            .GET()
            .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(HttpResponse::body)
            .thenApply(json -> {
                try {
                    return mapper.readValue(json, responseType);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to parse response", e);
                }
            });
    }

    public static CompletableFuture<UserIdDto> post(String username) {
        try {
            String jsonBody = "{\"username\": \"" + username + "\"}";
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/user"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(json -> {
                        try {
                            return mapper.readValue(json, UserIdDto.class);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to parse response", e);
                        }
                    }
                );
        } catch (Exception e) {
            return post(username);
        }
    }
}
