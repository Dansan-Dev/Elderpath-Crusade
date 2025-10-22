package io.github.forest_of_dreams.managers;

import com.badlogic.gdx.Gdx;
import io.github.forest_of_dreams.enums.settings.InputFunction;
import io.github.forest_of_dreams.enums.settings.InputHandlerData;
import io.github.forest_of_dreams.enums.settings.InputKey;
import io.github.forest_of_dreams.input_handlers.*;
import io.github.forest_of_dreams.interfaces.InputHandler;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * When adding a new piece of functionality, make sure to:
 * - Add it to the InputFunction enum
 * - Create a new handler (may be empty)
 * - Add the new pair to inputHandlers map in the initializeInputHandlers method
 *
 * When adding a new input key:
 * - Add it to the InputKey enum (with accurate key_code AND stating if it is key or mouse (button))
 * - If it has a standard/fixed binding, add it to the initialize method
 */
public class InputManager {
    // Maps input key to if it is pressed
    @Getter private static final Map<InputKey, Boolean> inputKeysPressed = new HashMap<>();

    // Maps input key to input func
    @Getter private static final Map<InputKey, InputFunction> inputKeyMappings = new HashMap<>();

    // Maps Input func to function handler
    @Getter private static final Map<InputFunction, InputHandler> inputHandlers = new HashMap<>();

    @Getter @Setter private static boolean isPaused = false;

    public static void initialize() {
        for (InputKey key : InputKey.values()) {
            setInput(key, InputFunction.UNBOUND);
        }
        // Fixed binding
        setInput(InputKey.MOUSE_LEFT, InputFunction.LEFT_CLICK);
        setInput(InputKey.MOUSE_RIGHT, InputFunction.RIGHT_CLICK);
        setInput(InputKey.ESCAPE, InputFunction.PAUSE_MENU);
        setInput(InputKey.Q, InputFunction.EXIT_GAME);
        setInput(InputKey.ENTER, InputFunction.CONFIRM_SELECTION);
        // Standard binding
        // ...

        initializeInputHandlers();
    }

    public static void initializeInputHandlers() {
        inputHandlers.put(InputFunction.LEFT_CLICK, new HandleLeftClick());
        inputHandlers.put(InputFunction.RIGHT_CLICK, new HandleRightClick());
        inputHandlers.put(InputFunction.PAUSE_MENU, new HandlePause());
        inputHandlers.put(InputFunction.EXIT_GAME, new HandleExitGame());
        inputHandlers.put(InputFunction.CONFIRM_SELECTION, new HandleConfirmSelection());

        inputHandlers.put(InputFunction.UNBOUND, new HandleUnbound());
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
        InputHandler handler = inputHandlers.get(func);
        handler.handleInput(data);
    }

    public static boolean getFunctionActivation(InputFunction func) {
        Optional<InputKey> optionalKey = inputKeyMappings.entrySet().stream()
            .filter(entry -> entry.getValue() == func)
            .findFirst()
            .map(Map.Entry::getKey);
        if(optionalKey.isEmpty()) return false;
        InputKey key = optionalKey.get();
        return inputKeysPressed.get(key);

    }
}
