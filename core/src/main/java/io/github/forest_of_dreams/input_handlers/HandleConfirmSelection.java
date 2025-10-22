package io.github.forest_of_dreams.input_handlers;

import io.github.forest_of_dreams.enums.settings.InputHandlerData;
import io.github.forest_of_dreams.interfaces.InputHandler;
import io.github.forest_of_dreams.managers.GraphicsManager;
import io.github.forest_of_dreams.managers.InteractionManager;

import java.util.Map;

/**
 * Handles confirmation of multi-selection interactions (Enter key).
 */
public class HandleConfirmSelection implements InputHandler {
    @Override
    public void handleInput(Map<InputHandlerData, Object> data) {
        // Do nothing if paused; interactions are cleared on pause entry.
        boolean isPaused = (boolean) data.get(InputHandlerData.IS_PAUSED);
        if (isPaused) return;
        InteractionManager.confirmSelection();
    }
}
