package io.github.elderpath_crusade.input_handlers;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.settings.InputHandlerData;
import io.github.elderpath_crusade.interfaces.InputHandler;
import io.github.elderpath_crusade.rendering.GraphicsManager;
import io.github.elderpath_crusade.input.InteractionManager;

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
        GameContext.get().getInteractionManager().confirmSelection();
    }
}
