package io.github.elderpath_crusade.input_handlers;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.settings.InputHandlerData;
import io.github.elderpath_crusade.enums.settings.PauseScreenPage;
import io.github.elderpath_crusade.game_objects.pause.PauseScreen;
import io.github.elderpath_crusade.interfaces.InputHandler;
import io.github.elderpath_crusade.managers.GameManager;
import io.github.elderpath_crusade.managers.GraphicsManager;
import io.github.elderpath_crusade.managers.InteractionManager;

import java.util.Map;

public class HandlePause implements InputHandler {
    @Override
    public void handleInput(Map<InputHandlerData, Object> data) {
        // When interactions are globally locked (e.g., win transition), ignore ESC entirely
        if (GameContext.get().getGameManager().isInteractionsLocked()) return;
        // If a multi-selection is in progress, first cancel it, then proceed to pause toggle
        if (GameContext.get().getInteractionManager().hasActiveSelection()) {
            GameContext.get().getInteractionManager().cancelSelection();
        }
        boolean isPaused = (boolean) data.get(InputHandlerData.IS_PAUSED);
        if (!isPaused) {
            GameContext.get().getGameManager().pause();
            PauseScreen.setCurrentPage(PauseScreenPage.MENU);
        } else {
            GameContext.get().getGameManager().unpause();
        }
    }
}
