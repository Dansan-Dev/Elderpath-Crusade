package io.github.forest_of_dreams.input_handlers;

import com.badlogic.gdx.Gdx;
import io.github.forest_of_dreams.enums.settings.InputHandlerData;
import io.github.forest_of_dreams.interfaces.InputHandler;

import java.util.Map;

public class HandleExitGame implements InputHandler {
    @Override
    public void handleInput(Map<InputHandlerData, Object> data) {
        Gdx.app.exit();
    }
}
