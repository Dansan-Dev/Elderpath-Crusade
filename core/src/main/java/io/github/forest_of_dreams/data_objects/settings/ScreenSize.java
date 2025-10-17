package io.github.forest_of_dreams.data_objects.settings;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import io.github.forest_of_dreams.Main;
import io.github.forest_of_dreams.managers.Game;
import io.github.forest_of_dreams.managers.GameManager;
import io.github.forest_of_dreams.managers.GraphicsManager;
import lombok.Getter;

public class ScreenSize {
    @Getter private final Viewport viewport = new ScreenViewport(new OrthographicCamera());
    @Getter private final int[] nonFullscreenSize = new int[]{1280, 720};
    private int[] currentSize = nonFullscreenSize;

    public void initialize() {
        viewport.update(getScreenWidth(), getScreenHeight(), true);
        viewport.apply();
    }

    public void toggleFullscreen() {
        Graphics graphics = Gdx.graphics;
        if (!graphics.isFullscreen()) updateToFullscreenSize();
        else updateToNonFullscreenSize();
        viewport.update(getScreenWidth(), getScreenHeight(), true);
        viewport.apply();
        if (Game.currentRoom != null) {
            Game.currentRoom.onScreenResize();
        }
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
        return getScreenConfiguredWidth();
    }

    public int getScreenHeight() {
        return getScreenConfiguredHeight();
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
