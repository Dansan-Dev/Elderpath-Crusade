package io.github.forest_of_dreams.api;

import lombok.Getter;

public class ResponseWrapper<T> {
    private final int code;
    @Getter private final T content;

    public ResponseWrapper(int code, T content) {
        this.code = code;
        this.content = content;
    }

    public boolean isCode(int code) {
        return this.code == code;
    }
}
