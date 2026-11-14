package io.github.elderpath_crusade.data_objects.settings;

import com.badlogic.gdx.Input;
import io.github.elderpath_crusade.enums.settings.InputFunction;
import io.github.elderpath_crusade.enums.settings.RebindResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure logic (FEAT-015/A3) — no Gdx app needed, just Input.Keys constants. */
class KeybindSettingTest {

    @Test
    void defaults_areEscapeAndEnter() {
        KeybindSetting keybinds = new KeybindSetting();
        assertEquals(Input.Keys.ESCAPE, keybinds.getKeycode(InputFunction.PAUSE_MENU));
        assertEquals(Input.Keys.ENTER, keybinds.getKeycode(InputFunction.CONFIRM_SELECTION));
    }

    @Test
    void rebind_toAFreeKey_succeeds() {
        KeybindSetting keybinds = new KeybindSetting();
        assertEquals(RebindResult.OK, keybinds.rebind(InputFunction.PAUSE_MENU, Input.Keys.P));
        assertEquals(Input.Keys.P, keybinds.getKeycode(InputFunction.PAUSE_MENU));
    }

    @Test
    void rebind_toTheKeyItAlreadyHolds_isANoOpSuccess() {
        KeybindSetting keybinds = new KeybindSetting();
        assertEquals(RebindResult.OK, keybinds.rebind(InputFunction.PAUSE_MENU, Input.Keys.ESCAPE));
        assertEquals(Input.Keys.ESCAPE, keybinds.getKeycode(InputFunction.PAUSE_MENU));
    }

    @Test
    void rebind_ontoTheOtherRebindableFunctionsKey_rejectedAndUnchanged() {
        KeybindSetting keybinds = new KeybindSetting();
        assertEquals(RebindResult.ALREADY_IN_USE, keybinds.rebind(InputFunction.CONFIRM_SELECTION, Input.Keys.ESCAPE));
        assertEquals(Input.Keys.ENTER, keybinds.getKeycode(InputFunction.CONFIRM_SELECTION), "unchanged on conflict");
    }

    @Test
    void rebind_ontoADeveloperOnlyFixedKey_rejected() {
        // Q = EXIT_GAME, F3 = TOGGLE_DIAGNOSTICS — fixed, never offered to the settings screen,
        // but still reserved against collision with a player-rebindable function.
        KeybindSetting keybinds = new KeybindSetting();
        assertEquals(RebindResult.ALREADY_IN_USE, keybinds.rebind(InputFunction.PAUSE_MENU, Input.Keys.Q));
        assertEquals(RebindResult.ALREADY_IN_USE, keybinds.rebind(InputFunction.PAUSE_MENU, Input.Keys.F3));
        assertEquals(Input.Keys.ESCAPE, keybinds.getKeycode(InputFunction.PAUSE_MENU));
    }

    @Test
    void rebind_aNonRebindableFunction_throws() {
        KeybindSetting keybinds = new KeybindSetting();
        assertThrows(IllegalArgumentException.class, () -> keybinds.rebind(InputFunction.EXIT_GAME, Input.Keys.P));
    }

    @Test
    void load_appliesSavedKeycodesWithoutConflictChecking() {
        KeybindSetting keybinds = new KeybindSetting();
        keybinds.load(Input.Keys.P, Input.Keys.SPACE);
        assertEquals(Input.Keys.P, keybinds.getKeycode(InputFunction.PAUSE_MENU));
        assertEquals(Input.Keys.SPACE, keybinds.getKeycode(InputFunction.CONFIRM_SELECTION));
    }

    @Test
    void rebindableFunctions_isExactlyPauseMenuAndConfirmSelection() {
        assertEquals(java.util.Set.of(InputFunction.PAUSE_MENU, InputFunction.CONFIRM_SELECTION),
                KeybindSetting.REBINDABLE_FUNCTIONS);
    }
}
