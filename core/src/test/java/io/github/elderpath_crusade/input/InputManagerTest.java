package io.github.elderpath_crusade.input;

import io.github.elderpath_crusade.enums.settings.InputFunction;
import io.github.elderpath_crusade.enums.settings.RebindResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FEAT-015/A3's capture state machine — pure bookkeeping, no Gdx.input needed (that part of
 * checkInput() is GUI-only and unverified here, same as the rest of this input layer).
 */
class InputManagerTest {

    @Test
    void notCapturing_initially() {
        InputManager inputManager = new InputManager();
        assertFalse(inputManager.isCapturing());
        assertNull(inputManager.getCapturingFunction());
    }

    @Test
    void startCapture_setsCapturingFunction() {
        InputManager inputManager = new InputManager();
        inputManager.startCapture(InputFunction.PAUSE_MENU, result -> {});
        assertTrue(inputManager.isCapturing());
        assertEquals(InputFunction.PAUSE_MENU, inputManager.getCapturingFunction());
    }

    @Test
    void cancelCapture_clearsState() {
        InputManager inputManager = new InputManager();
        inputManager.startCapture(InputFunction.CONFIRM_SELECTION, result -> {});
        inputManager.cancelCapture();
        assertFalse(inputManager.isCapturing());
        assertNull(inputManager.getCapturingFunction());
    }

    @Test
    void startCapture_aNonRebindableFunction_throwsAndLeavesStateUnchanged() {
        InputManager inputManager = new InputManager();
        assertThrows(IllegalArgumentException.class,
                () -> inputManager.startCapture(InputFunction.EXIT_GAME, result -> {}));
        assertFalse(inputManager.isCapturing());
    }

    @Test
    void startCapture_replacesAnyPreviousCapture() {
        InputManager inputManager = new InputManager();
        inputManager.startCapture(InputFunction.PAUSE_MENU, result -> fail("the first capture's callback must not fire"));
        inputManager.startCapture(InputFunction.CONFIRM_SELECTION, result -> {});
        assertEquals(InputFunction.CONFIRM_SELECTION, inputManager.getCapturingFunction());
    }
}
