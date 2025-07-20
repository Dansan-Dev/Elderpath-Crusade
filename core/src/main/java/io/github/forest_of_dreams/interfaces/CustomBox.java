package io.github.forest_of_dreams.interfaces;

import com.badlogic.gdx.Gdx;
import io.github.forest_of_dreams.managers.SettingsManager;

public interface CustomBox {
    int getX();

    int getY();

    int getWidth();

    int getHeight();

    default boolean inRange(int x, int y) {
        // Get the actual screen dimensions
        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();

        // Get the configured dimensions
        int configuredWidth = SettingsManager.screenSize.getScreenConfiguredWidth();
        int configuredHeight = SettingsManager.screenSize.getScreenConfiguredHeight();

        // Calculate scale factors
        float scaleX = configuredWidth / screenWidth;
        float scaleY = configuredHeight / screenHeight;

        // Scale the input coordinates
        float scaledX = x * scaleX;
        float scaledY = y * scaleY;

        boolean inRangeX = getX() <= scaledX && scaledX < getX() + getWidth();
        boolean inRangeY = getY() <= scaledY && scaledY < getY() + getHeight();
        return inRangeX && inRangeY;
    }
}
