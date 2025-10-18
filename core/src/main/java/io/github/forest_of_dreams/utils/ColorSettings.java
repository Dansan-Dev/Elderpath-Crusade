package io.github.forest_of_dreams.utils;

import com.badlogic.gdx.graphics.Color;
import lombok.Getter;

@Getter
public enum ColorSettings {
    // UI TEXT
    TEXT_DEFAULT(Color.WHITE),
    TEXT_HOVER(Color.YELLOW),
    TEXT_CLICK(Color.BLUE),

    // UI Buttons (Main menu, settings)
    BUTTON_PRIMARY(Color.valueOf("#81cce3")),
    BUTTON_HOVER(Color.valueOf("#b3d8e3")),
    BUTTON_BORDER(Color.GRAY),
    BUTTON_BORDER_HOVER(Color.WHITE),

    // Board/Plot colors
    PLOT_GREEN(Color.valueOf("#32943a")),
    PLOT_DIRT_BROWN(Color.valueOf("#473101")),
    PLOT_PLAYER_1_ROW(Color.BLUE),
    PLOT_PLAYER_2_ROW(Color.RED),
    BOARD_IDENTIFIER_SYMBOL_ROW(Color.ORANGE),
    BOARD_IDENTIFIER_SYMBOL_COL(Color.YELLOW);

    private final Color color;

    ColorSettings(Color color) {
        this.color = color;
    }
}
