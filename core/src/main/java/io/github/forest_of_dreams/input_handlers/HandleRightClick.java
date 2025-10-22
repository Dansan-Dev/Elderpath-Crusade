package io.github.forest_of_dreams.input_handlers;

import io.github.forest_of_dreams.enums.settings.InputHandlerData;
import io.github.forest_of_dreams.interfaces.InputHandler;
import io.github.forest_of_dreams.managers.InteractionManager;

import java.util.Map;

public class HandleRightClick implements InputHandler {
    @Override
    public void handleInput(Map<InputHandlerData, Object> data) {
        // Right-click serves as the primary way to cancel an in-progress multi-selection
        // Do nothing if paused; interactions are cleared on pause entry.
        boolean isPaused = (boolean) data.get(InputHandlerData.IS_PAUSED);
        if (isPaused) return;
        if (InteractionManager.hasActiveSelection()) {
            InteractionManager.cancelSelection();
        }
    }
}
