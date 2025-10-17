package io.github.forest_of_dreams.data_objects.settings;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import io.github.forest_of_dreams.Main;
import io.github.forest_of_dreams.managers.GameManager;
import io.github.forest_of_dreams.managers.GraphicsManager;
import lombok.Getter;

public class ScreenSize {
    @Getter private final int[] nonFullscreenSize = new int[]{1280, 720};
    private int[] currentSize = nonFullscreenSize;

    public void initialize() {}

    public void toggleFullscreen() {
        Graphics graphics = Gdx.graphics;
        if (!graphics.isFullscreen()) updateToFullscreenSize();
        else updateToNonFullscreenSize();
    }

    private void updateToNonFullscreenSize() {
        currentSize = nonFullscreenSize;
        Gdx.graphics.setWindowedMode(currentSize[0], currentSize[1]);
    }

    private void updateToFullscreenSize() {
        Graphics.DisplayMode displayMode = Gdx.graphics.getDisplayMode();
        Gdx.graphics.setFullscreenMode(displayMode);
        currentSize = new int[]{displayMode.width, displayMode.height};
    }

    public int getScreenWidth() {
        return Gdx.graphics.getWidth();
    }

    public int getScreenHeight() {
        return Gdx.graphics.getHeight();
    }

    public int[] getScreenSize() {
        return new int[]{getScreenWidth(), getScreenHeight()};
    }

    public int[] getScreenCenter() {
        return new int[]{currentSize[0] / 2, currentSize[1] / 2};
    }

    public int getScreenConfiguredWidth() {
        return currentSize[0];
    }

    public int getScreenConfiguredHeight() {
        return currentSize[1];
    }
}
