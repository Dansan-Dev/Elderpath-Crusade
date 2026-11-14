package io.github.elderpath_crusade.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.settings.KeybindSetting;
import io.github.elderpath_crusade.enums.settings.InputFunction;
import io.github.elderpath_crusade.enums.settings.InputHandlerData;
import io.github.elderpath_crusade.enums.settings.InputKey;
import io.github.elderpath_crusade.enums.settings.RebindResult;
import io.github.elderpath_crusade.input_handlers.*;
import io.github.elderpath_crusade.interfaces.InputHandler;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * When adding a new piece of functionality, make sure to:
 * - Add it to the InputFunction enum
 * - Create a new handler (may be empty)
 * - Add the new pair to inputHandlers map in the initializeInputHandlers method
 *
 * When adding a new input key:
 * - Add it to the InputKey enum (with accurate key_code AND stating if it is key or mouse (button))
 * - If it has a standard/fixed binding, add it to the initialize method
 *
 * PAUSE_MENU and CONFIRM_SELECTION are the exception: they're player-rebindable (FEAT-015/A3),
 * so their current key lives in {@code SettingsManager.keybinds} (any keyboard key, not a fixed
 * InputKey constant) rather than inputKeyMappings — see checkInput()/rebindableFunctionsPressed
 * and startCapture() below.
 */
public class InputManager {
    // Maps input key to if it is pressed
    @Getter private final Map<InputKey, Boolean> inputKeysPressed = new HashMap<>();

    // Maps input key to input func
    @Getter private final Map<InputKey, InputFunction> inputKeyMappings = new HashMap<>();

    // Maps Input func to function handler
    @Getter private final Map<InputFunction, InputHandler> inputHandlers = new HashMap<>();

    // Maps a rebindable InputFunction to whether its (dynamically bound) key was just pressed
    @Getter private final Map<InputFunction, Boolean> rebindableFunctionsPressed = new HashMap<>();

    @Getter @Setter private boolean isPaused = false;

    @Getter private InputFunction capturingFunction;
    private Consumer<RebindResult> captureCallback;

    public InputManager() {}

    public void initialize() {
        for (InputKey key : InputKey.values()) {
            setInput(key, InputFunction.UNBOUND);
        }
        // Fixed binding
        setInput(InputKey.MOUSE_LEFT, InputFunction.LEFT_CLICK);
        setInput(InputKey.MOUSE_RIGHT, InputFunction.RIGHT_CLICK);
        setInput(InputKey.Q, InputFunction.EXIT_GAME);
        setInput(InputKey.F3, InputFunction.TOGGLE_DIAGNOSTICS);
        // PAUSE_MENU / CONFIRM_SELECTION: player-rebindable (FEAT-015/A3) — see class doc above.
        // Standard binding
        // ...

        initializeInputHandlers();
    }

    public void initializeInputHandlers() {
        inputHandlers.put(InputFunction.LEFT_CLICK, new HandleLeftClick());
        inputHandlers.put(InputFunction.RIGHT_CLICK, new HandleRightClick());
        inputHandlers.put(InputFunction.PAUSE_MENU, new HandlePause());
        inputHandlers.put(InputFunction.EXIT_GAME, new HandleExitGame());
        inputHandlers.put(InputFunction.CONFIRM_SELECTION, new HandleConfirmSelection());
        inputHandlers.put(InputFunction.TOGGLE_DIAGNOSTICS, new HandleToggleDiagnostics());

        inputHandlers.put(InputFunction.UNBOUND, new HandleUnbound());
    }

    public void setInput(InputKey key, InputFunction value) {
        inputKeyMappings.put(key, value);
    }

    public InputFunction getInput(InputKey key) {
        return inputKeyMappings.get(key);
    }

    public void checkInput() {
        // Captured first and cached: if a rebind completes this frame, the physical key that
        // just triggered it must not ALSO fire its old function below (e.g. picking Q for
        // Pause Menu must not also quit the game via EXIT_GAME in the same frame).
        boolean wasCapturing = capturingFunction != null;
        if (wasCapturing) {
            Integer pressedKeycode = findJustPressedKeycode();
            if (pressedKeycode != null) {
                InputFunction function = capturingFunction;
                Consumer<RebindResult> callback = captureCallback;
                capturingFunction = null;
                captureCallback = null;
                RebindResult result = GameContext.get().getSettingsManager().keybinds.rebind(function, pressedKeycode);
                if (result == RebindResult.OK) GameContext.get().getSettingsManager().persist();
                if (callback != null) callback.accept(result);
            }
        }

        for (InputKey key : InputKey.values()) {
            boolean isPressed = (wasCapturing && key.isKey()) ? false
                    : (key.isKey() ?
                        Gdx.input.isKeyJustPressed(key.getKeyCode()) :
                        Gdx.input.isButtonJustPressed(key.getKeyCode()));
            inputKeysPressed.put(key, isPressed);
        }

        KeybindSetting keybinds = GameContext.get().getSettingsManager().keybinds;
        for (InputFunction function : KeybindSetting.REBINDABLE_FUNCTIONS) {
            boolean isPressed = !wasCapturing && Gdx.input.isKeyJustPressed(keybinds.getKeycode(function));
            rebindableFunctionsPressed.put(function, isPressed);
        }
    }

    public void activateInputHandler(InputKey key, Map<InputHandlerData, Object> data) {
        InputFunction func = inputKeyMappings.get(key);
        InputHandler handler = inputHandlers.get(func);
        handler.handleInput(data);
    }

    /** Dispatch for a rebindable function (PAUSE_MENU/CONFIRM_SELECTION) — no fixed InputKey to go through. */
    public void activateFunctionHandler(InputFunction func, Map<InputHandlerData, Object> data) {
        InputHandler handler = inputHandlers.get(func);
        if (handler != null) handler.handleInput(data);
    }

    public boolean getFunctionActivation(InputFunction func) {
        if (KeybindSetting.REBINDABLE_FUNCTIONS.contains(func)) {
            return Boolean.TRUE.equals(rebindableFunctionsPressed.get(func));
        }
        Optional<InputKey> optionalKey = inputKeyMappings.entrySet().stream()
            .filter(entry -> entry.getValue() == func)
            .findFirst()
            .map(Map.Entry::getKey);
        if(optionalKey.isEmpty()) return false;
        InputKey key = optionalKey.get();
        return inputKeysPressed.get(key);
    }

    // --- FEAT-015/A3: in-game key rebinding ---

    /** Starts listening for the next physical key press to bind to `function`; checkInput() consumes it and calls onComplete. */
    public void startCapture(InputFunction function, Consumer<RebindResult> onComplete) {
        if (!KeybindSetting.REBINDABLE_FUNCTIONS.contains(function)) {
            throw new IllegalArgumentException("not rebindable: " + function);
        }
        capturingFunction = function;
        captureCallback = onComplete;
    }

    public void cancelCapture() {
        capturingFunction = null;
        captureCallback = null;
    }

    public boolean isCapturing() {
        return capturingFunction != null;
    }

    private static Integer findJustPressedKeycode() {
        for (int code = 0; code <= Input.Keys.MAX_KEYCODE; code++) {
            if (Gdx.input.isKeyJustPressed(code)) return code;
        }
        return null;
    }
}
