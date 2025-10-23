package io.github.forest_of_dreams.managers;

import io.github.forest_of_dreams.enums.settings.PauseScreenPage;
import io.github.forest_of_dreams.game_objects.pause.PauseScreen;
import lombok.Getter;

public class GameManager {
    @Getter private static boolean isPaused = false;

    public static void initialize() {
        SettingsManager.initialize();
        ShaderManager.initialize();
        InputManager.initialize();
        // Initialize simple bot listener (idempotent)
        BotManager.initialize();
    }

    public static void pause() {
        isPaused = true;
        pauseGraphics();
        pauseInputHandlers();
    }

    public static void unpause() {
        isPaused = false;
        unpauseGraphics();
        unpauseInputHandlers();
        PauseScreen.setCurrentPage(PauseScreenPage.NONE);
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
