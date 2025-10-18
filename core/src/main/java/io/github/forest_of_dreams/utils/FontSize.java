package io.github.forest_of_dreams.utils;

import lombok.Getter;

@Getter
public enum FontSize {
    // Titles / Headers
    TITLE_LARGE(36),
    TITLE_MEDIUM(24),

    // Body / Menu options
    BODY_LARGE(18),
    BODY_MEDIUM(16),
    CAPTION(12),

    // Buttons
    BUTTON_DEFAULT(10);

    private final int size;

    FontSize(int size) {
        this.size = size;
    }
}
