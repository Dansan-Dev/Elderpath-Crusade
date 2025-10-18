package io.github.forest_of_dreams.utils;

import com.badlogic.gdx.graphics.Color;
import lombok.Getter;

@Getter
public enum ColorScheme {
    WHITE(Color.WHITE),
    BLACK(Color.BLACK);

    private final Color color;

    ColorScheme(Color color) {
        this.color = color;
    }
}
