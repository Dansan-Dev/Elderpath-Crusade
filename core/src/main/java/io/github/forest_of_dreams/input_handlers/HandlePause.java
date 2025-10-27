package io.github.forest_of_dreams.input_handlers;

import io.github.forest_of_dreams.enums.settings.InputHandlerData;
import io.github.forest_of_dreams.enums.settings.PauseScreenPage;
import io.github.forest_of_dreams.game_objects.pause.PauseScreen;
import io.github.forest_of_dreams.interfaces.InputHandler;
import io.github.forest_of_dreams.managers.GameManager;
import io.github.forest_of_dreams.managers.GraphicsManager;
import io.github.forest_of_dreams.managers.InteractionManager;

import java.util.Map;

public class HandlePause implements InputHandler {
    @Override
    public void handleInput(Map<InputHandlerData, Object> data) {
        // When interactions are globally locked (e.g., win transition), ignore ESC entirely
        if (GameManager.isInteractionsLocked()) return;
        // If a multi-selection is in progress, first cancel it, then proceed to pause toggle
        if (InteractionManager.hasActiveSelection()) {
            InteractionManager.cancelSelection();
        }
        boolean isPaused = (boolean) data.get(InputHandlerData.IS_PAUSED);
        if (!isPaused) {
            GameManager.pause();
            PauseScreen.setCurrentPage(PauseScreenPage.MENU);
        } else {
            GameManager.unpause();
        }
    }
}
