package io.github.elderpath_crusade.managers;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.settings.PauseScreenPage;
import io.github.elderpath_crusade.game_objects.cards.CardFactory;
import io.github.elderpath_crusade.game_objects.pause.PauseScreen;
import lombok.Getter;

public class GameManager {
    @Getter private boolean isPaused = false;
    @Getter private boolean interactionsLocked = false;

    public GameManager() {}

    public void initialize() {
        SettingsManager.initialize();
        ShaderManager.initialize();
        InputManager.initialize();
        GameContext.get().getBotManager().initialize();
        GameContext.get().getWinConditionManager().initialize();
        CardFactory.initialize();
        GameContext.get().getVictoryHandler().initialize();
    }

    public void pause() {
        isPaused = true;
        GraphicsManager.pauseAnimations();
        pauseInputHandlers();
    }

    public void unpause() {
        isPaused = false;
        GraphicsManager.unpauseAnimations();
        unpauseInputHandlers();
        PauseScreen.setCurrentPage(PauseScreenPage.NONE);
    }

    public void lockInteractions() {
        interactionsLocked = true;
        InputManager.setPaused(true);
    }

    public void unlockInteractions() {
        interactionsLocked = false;
        InputManager.setPaused(false);
    }

    private void pauseInputHandlers() {
        InputManager.setPaused(true);
    }

    private void unpauseInputHandlers() {
        InputManager.setPaused(false);
    }
}
