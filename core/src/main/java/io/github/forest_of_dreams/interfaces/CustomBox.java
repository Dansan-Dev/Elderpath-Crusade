package io.github.forest_of_dreams.interfaces;

import com.badlogic.gdx.Gdx;
import io.github.forest_of_dreams.managers.SettingsManager;

public interface CustomBox {
    int getX();

    int getY();

    int getWidth();

    int getHeight();

    default boolean inRange(int x, int y) {
        boolean inRangeX = getX() <= x && x < getX() + getWidth();
        boolean inRangeY = getY() <= y && y < getY() + getHeight();
        return inRangeX && inRangeY;
    }
}
