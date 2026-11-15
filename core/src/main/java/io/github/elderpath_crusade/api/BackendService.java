package io.github.elderpath_crusade.api;

import io.github.elderpath_crusade.api.dto.UserIdDto;
import io.github.elderpath_crusade.api.dto.UserListResponseDto;

import java.util.HashMap;
import java.util.List;


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
