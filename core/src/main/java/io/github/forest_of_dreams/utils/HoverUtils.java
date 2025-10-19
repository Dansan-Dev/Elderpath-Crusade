package io.github.forest_of_dreams.utils;

import com.badlogic.gdx.Gdx;

/**
 * Small UI helper utilities for pointer hit-testing and related interactions.
 */
public final class HoverUtils {
    private HoverUtils() {}

    /**
     * Returns true if the current mouse position lies within the given rectangle (inclusive bounds).
     * The y-coordinate is expected in world/screen coordinates where origin is bottom-left,
     * matching how most of this project calculates positions (using Gdx.graphics height flip).
     */
    public static boolean isHovered(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return false;
        float mouseX = Gdx.input.getX();
        float mouseY = Gdx.graphics.getHeight() - Gdx.input.getY();
        boolean inX = x <= mouseX && mouseX <= (x + width - 1);
        boolean inY = y <= mouseY && mouseY <= (y + height - 1);
        return inX && inY;
    }
}
