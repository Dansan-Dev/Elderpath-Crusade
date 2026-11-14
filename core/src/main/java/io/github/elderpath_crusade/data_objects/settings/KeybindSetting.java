package io.github.elderpath_crusade.data_objects.settings;

import com.badlogic.gdx.Input;
import io.github.elderpath_crusade.enums.settings.InputFunction;
import io.github.elderpath_crusade.enums.settings.InputKey;
import io.github.elderpath_crusade.enums.settings.RebindResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * FEAT-015/A3: which physical keyboard key triggers each player-rebindable {@link
 * InputFunction}. Only {@code PAUSE_MENU} and {@code CONFIRM_SELECTION} are rebindable here —
 * {@code EXIT_GAME} (Q) and {@code TOGGLE_DIAGNOSTICS} (F3) are developer-only bindings that
 * stay fixed in {@link InputKey} and are never exposed to this class or the settings screen.
 * Plain data, no Gdx app/graphics dependency (just {@code Input.Keys} constants), so it's
 * headlessly testable like {@code SoundSetting}/{@code LanguageSetting}.
 */
public class KeybindSetting {
    public static final int DEFAULT_PAUSE_MENU_KEYCODE = Input.Keys.ESCAPE;
    public static final int DEFAULT_CONFIRM_SELECTION_KEYCODE = Input.Keys.ENTER;

    public static final Set<InputFunction> REBINDABLE_FUNCTIONS =
            Set.of(InputFunction.PAUSE_MENU, InputFunction.CONFIRM_SELECTION);

    private final Map<InputFunction, Integer> keycodes = new HashMap<>(Map.of(
            InputFunction.PAUSE_MENU, DEFAULT_PAUSE_MENU_KEYCODE,
            InputFunction.CONFIRM_SELECTION, DEFAULT_CONFIRM_SELECTION_KEYCODE));

    public int getKeycode(InputFunction function) {
        return keycodes.get(function);
    }

    /** Applies previously-saved keycodes on launch restore — bypasses the usual conflict check, since a saved pair is assumed consistent. */
    public void load(int pauseMenuKeycode, int confirmSelectionKeycode) {
        keycodes.put(InputFunction.PAUSE_MENU, pauseMenuKeycode);
        keycodes.put(InputFunction.CONFIRM_SELECTION, confirmSelectionKeycode);
    }

    /** Rebinds `function` to `newKeycode`, rejecting it if any other binding (fixed or rebindable) already owns that key. */
    public RebindResult rebind(InputFunction function, int newKeycode) {
        if (!REBINDABLE_FUNCTIONS.contains(function)) {
            throw new IllegalArgumentException("not rebindable: " + function);
        }
        if (isInUseByAnotherBinding(newKeycode, function)) return RebindResult.ALREADY_IN_USE;
        keycodes.put(function, newKeycode);
        return RebindResult.OK;
    }

    private boolean isInUseByAnotherBinding(int keycode, InputFunction excluding) {
        for (InputKey key : InputKey.values()) {
            if (key.isKey() && key.getKeyCode() == keycode) return true; // Q / F3 — developer-only, never reassignable
        }
        for (Map.Entry<InputFunction, Integer> entry : keycodes.entrySet()) {
            if (entry.getKey() != excluding && entry.getValue() == keycode) return true;
        }
        return false;
    }
}
