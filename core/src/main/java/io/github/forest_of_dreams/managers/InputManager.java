package io.github.forest_of_dreams.managers;

import com.badlogic.gdx.Gdx;
import io.github.forest_of_dreams.enums.settings.InputFunction;
import io.github.forest_of_dreams.enums.settings.InputHandlerData;
import io.github.forest_of_dreams.enums.settings.InputKey;
import io.github.forest_of_dreams.input_handlers.HandlePause;
import io.github.forest_of_dreams.input_handlers.HandleUnbound;
import io.github.forest_of_dreams.interfaces.InputHandler;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

public class InputManager {
    @Getter private static final Map<InputKey, InputFunction> inputKeyMappings = new HashMap<>();
    @Getter private static final Map<InputKey, Boolean> inputKeysPressed = new HashMap<>();
    @Getter private static final Map<InputFunction, InputHandler> functionHandlers = new HashMap<>();
    @Getter @Setter private static boolean isPaused = false;

    public static void initialize() {
        for (InputKey key : InputKey.values()) {
            setInput(key, InputFunction.UNBOUND);
        }
        setInput(InputKey.ESCAPE, InputFunction.PAUSE_MENU);

        setStandardInputHandlers();
    }

    public static void setStandardInputHandlers() {
        functionHandlers.put(InputFunction.UNBOUND, new HandleUnbound());
        functionHandlers.put(InputFunction.PAUSE_MENU, new HandlePause());
    }

    public static void setInput(InputKey key, InputFunction value) {
        inputKeyMappings.put(key, value);
    }

    public static InputFunction getInput(InputKey key) {
        return inputKeyMappings.get(key);
    }

    public static void checkInput() {
        for (InputKey key : InputKey.values()) {
            boolean isPressed = (key.isKey() ?
                Gdx.input.isKeyJustPressed(key.getKeyCode()) :
                Gdx.input.isButtonJustPressed(key.getKeyCode()));
            inputKeysPressed.put(key, isPressed);
        }
    }

    public static void activateInputHandler(InputKey key, Map<InputHandlerData, Object> data) {
        InputFunction func = inputKeyMappings.get(key);
        InputHandler handler = functionHandlers.get(func);
        handler.handleInput(data);
    }
}
