package io.github.forest_of_dreams.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.forest_of_dreams.api.dto.UserIdDto;
import io.github.forest_of_dreams.api.dto.UserListResponseDto;
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
import java.util.concurrent.CompletableFuture;


public class BackendService {
    private static final BaseURL BASE_URL = BaseURL.BACKEND;
    private static boolean isUp = false;

    public static boolean isUp() {
        boolean isUp = UtilsApi.getHealth(BASE_URL, 10);
        BackendService.isUp = isUp;
        return isUp;
    }

    public static UserListResponseDto getUsers() {
        if (!isUp) return null;
        return UtilsApi.get(
            BASE_URL,
            new HashMap<>(),
            List.of(200),
            UserListResponseDto.class,
            "/user",
            10
        );
    }

    public static UserIdDto postUser(HashMap<String, Object> data) {
        if (!isUp) return null;
        return UtilsApi.post(
            BASE_URL,
            data,
            List.of(201),
            UserIdDto.class,
            "/user",
            10
        );
    }


}
