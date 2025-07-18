package io.github.forest_of_dreams.enums.settings;

import com.badlogic.gdx.Input;
import lombok.Getter;

@Getter
public enum InputKey {
    MOUSE_LEFT(Input.Buttons.LEFT, false),
    MOUSE_RIGHT(Input.Buttons.RIGHT, false),
    ESCAPE(Input.Keys.ESCAPE, true),
    ENTER(Input.Keys.ENTER, true),
    Q(Input.Keys.Q, true);

    private final int keyCode;
    private final boolean isKey;

    InputKey(int keyCode, boolean isKey) {
        this.keyCode = keyCode;
        this.isKey = isKey;
    }
}
