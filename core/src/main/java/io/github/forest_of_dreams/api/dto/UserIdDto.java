package io.github.forest_of_dreams.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.UUID;

public class UserIdDto {
    @Getter private final UUID id;

    @JsonCreator
    public UserIdDto(
        @JsonProperty String id
    ) {
        this.id = UUID.fromString(id.replace("\"", ""));
    }
}
