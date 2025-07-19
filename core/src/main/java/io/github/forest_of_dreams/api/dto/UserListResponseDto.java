package io.github.forest_of_dreams.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

public class UserListResponseDto {
    @Getter List<UserResponseDto> users;

    @JsonCreator
    public UserListResponseDto(
        @JsonProperty("data") List<UserResponseDto> users
    ) {
        this.users = users;
    }
}
