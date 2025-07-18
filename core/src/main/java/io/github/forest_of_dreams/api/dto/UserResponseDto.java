package io.github.forest_of_dreams.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.UUID;

public class UserResponseDto {
    @Getter private final UUID id;
    @Getter private final String username;

    @JsonCreator
    public UserResponseDto(
        @JsonProperty("id") String id,
        @JsonProperty("username") String username
    ) {
        this.id = UUID.fromString(id);
        this.username = username;
    }
}
