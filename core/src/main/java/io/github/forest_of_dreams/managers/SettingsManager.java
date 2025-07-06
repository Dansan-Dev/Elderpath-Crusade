package io.github.forest_of_dreams.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import lombok.Getter;
import lombok.Setter;

public class SettingsManager {
    private static final Graphics graphics = Gdx.graphics;

    @Getter @Setter
    private static int SCREEN_WIDTH = 1280;
    @Getter @Setter
    private static int SCREEN_HEIGHT = 720;
    @Setter
    private static int FPS = 60;

    public static int[] getScreenSize() {
        return new int[]{SCREEN_WIDTH, SCREEN_HEIGHT};
    }

    public static int[] getScreenCenter() {
        return new int[]{SCREEN_WIDTH / 2, SCREEN_HEIGHT / 2};
    }

    public static int getScreenWidth() {
        return SCREEN_WIDTH;
    }

    public static int getScreenHeight() {
        return SCREEN_HEIGHT;
    }

    public static void setScreenSize(int width, int height) {
        SCREEN_WIDTH = width;
        SCREEN_HEIGHT = height;
        if (!graphics.isFullscreen())
            graphics.setWindowedMode(SCREEN_WIDTH, SCREEN_HEIGHT);
    }

    public static void toggleFullscreen() {
        if (graphics.isFullscreen())
            graphics.setFullscreenMode(graphics.getDisplayMode());
        else
            graphics.setWindowedMode(SCREEN_WIDTH, SCREEN_HEIGHT);
    }
}
