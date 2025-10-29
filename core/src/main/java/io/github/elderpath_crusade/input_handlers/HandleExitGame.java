package io.github.elderpath_crusade.input_handlers;

import com.badlogic.gdx.Gdx;
import io.github.elderpath_crusade.enums.settings.InputHandlerData;
import io.github.elderpath_crusade.interfaces.InputHandler;

import java.util.Map;

public class HandleExitGame implements InputHandler {
    @Override
    public void handleInput(Map<InputHandlerData, Object> data) {
        Gdx.app.exit();
    }
}
