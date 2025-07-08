package io.github.forest_of_dreams.managers;

import io.github.forest_of_dreams.game_objects.SpriteObject;
import lombok.Getter;

public class GameManager {
    @Getter private static boolean isPaused = false;

    public static void pause(GraphicsManager graphicsManager) {
        isPaused = true;
        pauseGraphics(graphicsManager);
    }

    public static void unpause(GraphicsManager graphicsManager) {
        isPaused = false;
        unpauseGraphics(graphicsManager);
    }

    private static void pauseGraphics(GraphicsManager graphicsManager) {
        graphicsManager.pause();
    }

    private static void unpauseGraphics(GraphicsManager graphicsManager) {
        graphicsManager.unpause();
    }
}
