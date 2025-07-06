package io.github.forest_of_dreams.enums;

public enum SpriteBoxPos {
    CENTER,
    TOP_LEFT, TOP, TOP_RIGHT,
    BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT,
    LEFT,
    RIGHT;

    public int[] toVector() {
        int x;
        int y;

        if (this == TOP_LEFT || this == TOP || this == TOP_RIGHT) y = 1;
        else if (this == BOTTOM_LEFT || this == BOTTOM || this == BOTTOM_RIGHT) y = -1;
        else y = 0;

        if (this == TOP_LEFT || this == LEFT || this == BOTTOM_LEFT) x = -1;
        else if (this == TOP_RIGHT || this == RIGHT || this == BOTTOM_RIGHT) x = 1;
        else x = 0;

        return new int[]{x, y};
    }
}
