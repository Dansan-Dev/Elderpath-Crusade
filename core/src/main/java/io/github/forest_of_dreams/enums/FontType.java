package io.github.forest_of_dreams.enums;

import lombok.Getter;

@Getter
public enum FontType {
    DEFAULT("default"),
    SILKSCREEN("Silkscreen-Regular"),
    WINDOW("window"),
    LIST("list"),
    SUBTITLE("subtitle");

    private final String fontName;

    private FontType(String fontName) {
        this.fontName = fontName;
    }
}
