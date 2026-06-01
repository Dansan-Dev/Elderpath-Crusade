package io.github.elderpath_crusade.input_handlers;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.settings.InputHandlerData;
import io.github.elderpath_crusade.interfaces.InputHandler;

import java.util.Map;

public class HandleLeftClick implements InputHandler {
    @Override
    public void handleInput(Map<InputHandlerData, Object> data) {
        int mouseX = (int) data.get(InputHandlerData.MOUSE_X);
        int mouseY = (int) data.get(InputHandlerData.MOUSE_Y);
        boolean paused = (boolean) data.get(InputHandlerData.IS_PAUSED);

        GameContext.get().getInteractionManager().processLeftClick(mouseX, mouseY, paused);
    }
}
