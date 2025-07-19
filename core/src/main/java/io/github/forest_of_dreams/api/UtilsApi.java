package io.github.forest_of_dreams.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.forest_of_dreams.exceptions.ApiCallException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.StringJoiner;

public class UtilsApi {
    private static final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static <T> T get(BaseURL baseURL, HashMap<String, Object> data, List<Integer> expectedCodes, Class<T> responseType, String endpoint, int maxRequests) {
        String json = convertToJsonString(data);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseURL.getBaseUrl() + endpoint))
            .header("Content-Type", "application/json")
            .method("GET", HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> response = sendRequest(request, expectedCodes, maxRequests, 0);
        return convertToType(response, responseType);
    }

    public static <T> T post(BaseURL baseURL, HashMap<String, Object> data, List<Integer> expectedCodes, Class<T> responseType, String endpoint, int maxRequests) {
        String json = convertToJsonString(data);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseURL.getBaseUrl() + endpoint))
            .header("Content-Type", "application/json")
            .method("POST", HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> response = sendRequest(request, expectedCodes, maxRequests, 0);
        return convertToType(response, responseType);
    }

    private static HttpResponse<String> sendRequest(HttpRequest request, List<Integer> expectedCodes, int maxRequests, int count) {
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (!expectedCodes.contains(response.statusCode())) {
                throw new RuntimeException("Non-accepted response code from backend: " + response.statusCode());
            }
            return response;
        } catch (IOException | InterruptedException e) {
            if (count < maxRequests) sendRequest(request, expectedCodes, maxRequests, count + 1);
            throw new ApiCallException(e.getMessage());
        }
    }

    private static <T> T convertToType(HttpResponse<String> response, Class<T> responseType) {
        try {
            return mapper.readValue(response.body(), responseType);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String convertToJsonString(HashMap<String, Object> data) {
        StringJoiner jsonJoiner = new StringJoiner(",", "{", "}");
        for (HashMap.Entry<String, Object> entry : data.entrySet()) {
            jsonJoiner.add("\"" + entry.getKey() + "\": \"" + entry.getValue() + "\"");
        }
        return jsonJoiner.toString();
    }
}
