package io.github.forest_of_dreams.data_objects.settings;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import lombok.Getter;

public class ScreenSize {
    private int[] currentSize = new int[]{1280, 720};
    @Getter
    private int[] nonFullscreenSize = currentSize;

    public int[] getScreenCenter() {
        return new int[]{currentSize[0] / 2, currentSize[1] / 2};
    }

    public void setNonFullscreenSize(int width, int height) {
        nonFullscreenSize = new int[]{width, height};
        if (!Gdx.graphics.isFullscreen()) {
            updateToNonFullscreenSize();
        }
    }

    public void toggleFullscreen() {
        Graphics graphics = Gdx.graphics;
        if (!graphics.isFullscreen()) {
            currentSize = new int[]{graphics.getWidth(), graphics.getHeight()};
            graphics.setFullscreenMode(graphics.getDisplayMode());
        }
        else {
            updateToNonFullscreenSize();
        }
    }

    private void updateToNonFullscreenSize() {
        currentSize = nonFullscreenSize;
        Gdx.graphics.setWindowedMode(currentSize[0], currentSize[1]);
    }

    public int getScreenWidth() {
        return Gdx.graphics.getWidth();//screenSize.getCurrentSize()[0];
    }

    public int getScreenHeight() {
        return Gdx.graphics.getHeight();//screenSize.getCurrentSize()[1];
    }

    public int[] getScreenSize() {
        return new int[]{getScreenWidth(), getScreenHeight()};
    }

    public int getScreenConfiguredWidth() {
        return currentSize[0];
    }

    public int getScreenConfiguredHeight() {
        return currentSize[1];
    }
}
