package io.github.forest_of_dreams.input_handlers;

import io.github.forest_of_dreams.enums.settings.InputHandlerData;
import io.github.forest_of_dreams.interfaces.InputHandler;
import io.github.forest_of_dreams.managers.GameManager;
import io.github.forest_of_dreams.managers.GraphicsManager;

import java.util.Map;

public class HandlePause implements InputHandler {

    @Override
    public void handleInput(Map<InputHandlerData, Object> data) {
        boolean isPaused = (boolean) data.get(InputHandlerData.IS_PAUSED);
        if (!isPaused) {
            GameManager.pause();
        } else {
            GameManager.unpause();
        }
    }
}
