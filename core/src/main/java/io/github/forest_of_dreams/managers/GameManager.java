package io.github.forest_of_dreams.managers;

import lombok.Getter;

public class GameManager {
    @Getter private static boolean isPaused = false;

    public static void pause() {
        isPaused = true;
        pauseGraphics();
        pauseInputHandlers();
    }

    public static void unpause() {
        isPaused = false;
        unpauseGraphics();
        pauseInputHandlers();
    }

    private static void pauseGraphics() {
        GraphicsManager.pause();
    }

    private static void unpauseGraphics() {
        GraphicsManager.unpause();
    }

    private static void pauseInputHandlers() {
        InputManager.setPaused(true);
    }

    private static void unpauseInputHandlers() {
        InputManager.setPaused(false);
    }
}
