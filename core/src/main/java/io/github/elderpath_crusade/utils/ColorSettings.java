package io.github.elderpath_crusade.utils;

import com.badlogic.gdx.graphics.Color;
import lombok.Getter;

@Getter
public enum ColorSettings {
    // UI TEXT
    TEXT_DEFAULT(Color.WHITE),
    TEXT_HOVER(Color.YELLOW),
    TEXT_CLICK(Color.BLUE),
    /** Rejected-action feedback (FEAT-015/B3) — a distinct "something didn't work" color. */
    TEXT_WARNING(Color.valueOf("#e35d5d")),
    /** Main-menu title lettering on the carved banner (UI-007): wheat/cream, with a dark outline. */
    TITLE_TEXT(Color.valueOf("#f4e9c8")),
    TITLE_OUTLINE(Color.valueOf("#20100a")),

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
    BOARD_IDENTIFIER_SYMBOL_COL(Color.YELLOW),

    // Roguelike run map nodes (RunRoom)
    RUN_NODE_BATTLE(Color.valueOf("#4a7fb5")),
    RUN_NODE_ELITE(Color.valueOf("#c1440e")),
    RUN_NODE_RECRUITMENT(Color.valueOf("#3a9d55")),
    RUN_NODE_EVENT(Color.valueOf("#7a4fa0")),
    RUN_NODE_BOSS(Color.valueOf("#6b1e3c")),
    RUN_MAP_EDGE(Color.valueOf("#3a3a3a")),
    RUN_MAP_EDGE_TRAVELED(Color.valueOf("#9a9a9a")),
    /** A run map node's border when it's currently pickable — the node's fill always shows its
     *  type color (RUN_NODE_BATTLE etc.); only the border signals "you can choose this now". */
    RUN_NODE_PICKABLE_BORDER(Color.GOLD);

    private final Color color;

    ColorSettings(Color color) {
        this.color = color;
    }
}
