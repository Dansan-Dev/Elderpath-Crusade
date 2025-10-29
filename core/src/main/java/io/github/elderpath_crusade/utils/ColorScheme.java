package io.github.elderpath_crusade.utils;

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
