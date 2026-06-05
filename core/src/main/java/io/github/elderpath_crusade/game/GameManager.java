package io.github.elderpath_crusade.game;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data.AbilityRegistry;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.enums.settings.PauseScreenPage;
import io.github.elderpath_crusade.game_objects.cards.CardFactory;
import io.github.elderpath_crusade.game_objects.pause.PauseScreen;
import lombok.Getter;

public class GameManager {
    @Getter private boolean isPaused = false;
    @Getter private boolean interactionsLocked = false;

    public GameManager() {}

    public void initialize() {
        GameContext.get().getSettingsManager().initialize();
        GameContext.get().getShaderManager().initialize();
        GameContext.get().getInputManager().initialize();
        GameContext.get().getBotManager().initialize();
        GameContext.get().getWinConditionManager().initialize();
        PieceRegistry.load();
        AbilityRegistry.load();
        CardFactory.initialize();
    }

    public void pause() {
        isPaused = true;
        GameContext.get().getGraphicsManager().pauseAnimations();
        pauseInputHandlers();
    }

    public void unpause() {
        isPaused = false;
        GameContext.get().getGraphicsManager().unpauseAnimations();
        unpauseInputHandlers();
        PauseScreen.setCurrentPage(PauseScreenPage.NONE);
    }

    public void lockInteractions() {
        interactionsLocked = true;
        GameContext.get().getInputManager().setPaused(true);
    }

    public void unlockInteractions() {
        interactionsLocked = false;
        GameContext.get().getInputManager().setPaused(false);
    }

    private void pauseInputHandlers() {
        GameContext.get().getInputManager().setPaused(true);
    }

    private void unpauseInputHandlers() {
        GameContext.get().getInputManager().setPaused(false);
    }
}
