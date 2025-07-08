package io.github.forest_of_dreams.managers;

import com.badlogic.gdx.Gdx;
import io.github.forest_of_dreams.enums.settings.InputFunction;
import io.github.forest_of_dreams.enums.settings.InputKey;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

public class InputManager {
    @Getter private static final Map<InputKey, InputFunction> inputKeyMappings = new HashMap<>();
    @Getter private static final Map<InputKey, Boolean> inputKeysPressed = new HashMap<>();

    public static void initialize() {
        for (InputKey key : InputKey.values()) {
            setInput(key, null);
        }
        setInput(InputKey.ESCAPE, InputFunction.PAUSE_MENU);
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
}
