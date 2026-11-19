package io.github.elderpath_crusade.enums;

import lombok.Getter;

@Getter
public enum FontType {
    SILKSCREEN("Silkscreen-Regular");

    private final String fontName;

    private FontType(String fontName) {
        this.fontName = fontName;
    }
}
