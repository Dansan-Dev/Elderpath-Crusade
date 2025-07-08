package io.github.forest_of_dreams.interfaces;

import io.github.forest_of_dreams.enums.settings.InputHandlerData;

import java.util.Map;

public interface InputHandler {
    void handleInput(Map<InputHandlerData, Object> data);
}
