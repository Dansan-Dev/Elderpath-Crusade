package io.github.elderpath_crusade.enums.settings;

import com.badlogic.gdx.Input;
import lombok.Getter;

/**
 * Fixed, non-rebindable physical inputs. PAUSE_MENU (Escape) and CONFIRM_SELECTION (Enter) are
 * NOT here — they're player-rebindable (FEAT-015/A3) and tracked by keycode in
 * {@code KeybindSetting} instead, since a rebindable function can be reassigned to any
 * keyboard key, not just a fixed enum constant. Q (EXIT_GAME) and F3 (TOGGLE_DIAGNOSTICS) stay
 * here deliberately — they're developer-only bindings, never exposed to the settings screen.
 */
@Getter
public enum InputKey {
    MOUSE_LEFT(Input.Buttons.LEFT, false),
    MOUSE_RIGHT(Input.Buttons.RIGHT, false),
    Q(Input.Keys.Q, true),
    F3(Input.Keys.F3, true);

    private final int keyCode;
    private final boolean isKey;

    InputKey(int keyCode, boolean isKey) {
        this.keyCode = keyCode;
        this.isKey = isKey;
    }
}
