package io.github.elderpath_crusade.interfaces;

import io.github.elderpath_crusade.enums.settings.InputHandlerData;

import java.util.Map;

public interface InputHandler {
    void handleInput(Map<InputHandlerData, Object> data);
}
