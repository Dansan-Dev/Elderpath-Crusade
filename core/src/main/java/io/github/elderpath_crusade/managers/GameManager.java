package io.github.elderpath_crusade.managers;

import io.github.elderpath_crusade.enums.settings.PauseScreenPage;
import io.github.elderpath_crusade.game_objects.cards.CardFactory;
import io.github.elderpath_crusade.game_objects.pause.PauseScreen;
import lombok.Getter;

public class GameManager {
    @Getter private static boolean isPaused = false;
    @Getter private static boolean interactionsLocked = false;

    public static void initialize() {
        SettingsManager.initialize();
        ShaderManager.initialize();
        InputManager.initialize();
        // Initialize simple bot listener (idempotent)
        BotManager.initialize();
        // Initialize win condition watcher (idempotent)
        WinConditionManager.initialize();
        CardFactory.initialize();
        VictoryHandler.initialize();
    }

    public static void pause() {
        isPaused = true;
        GraphicsManager.pauseAnimations();
        pauseInputHandlers();
    }

    public static void unpause() {
        isPaused = false;
        GraphicsManager.unpauseAnimations();
        unpauseInputHandlers();
        PauseScreen.setCurrentPage(PauseScreenPage.NONE);
    }

    // Interaction lock: blocks all input processing without showing pause UI
    public static void lockInteractions() {
        interactionsLocked = true;
        InputManager.setPaused(true);
    }

    public static void unlockInteractions() {
        interactionsLocked = false;
        InputManager.setPaused(false);
    }

    private static void pauseInputHandlers() {
        InputManager.setPaused(true);
    }

    private static void unpauseInputHandlers() {
        InputManager.setPaused(false);
    }
}
