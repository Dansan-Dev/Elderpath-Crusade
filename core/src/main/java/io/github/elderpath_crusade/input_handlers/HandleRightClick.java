package io.github.elderpath_crusade.input_handlers;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.settings.InputHandlerData;
import io.github.elderpath_crusade.interfaces.InputHandler;

import java.util.Map;

public class HandleRightClick implements InputHandler {
    @Override
    public void handleInput(Map<InputHandlerData, Object> data) {
        // Right-click serves as the primary way to cancel an in-progress multi-selection
        // Do nothing if paused; interactions are cleared on pause entry.
        boolean isPaused = (boolean) data.get(InputHandlerData.IS_PAUSED);
        if (isPaused) return;
        if (GameContext.get().getInteractionManager().hasActiveSelection()) {
            GameContext.get().getInteractionManager().cancelSelection();
        }
    }
}
