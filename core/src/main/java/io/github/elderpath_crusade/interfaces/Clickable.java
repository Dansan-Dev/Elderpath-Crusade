package io.github.elderpath_crusade.interfaces;

import io.github.elderpath_crusade.data_objects.ClickableEffectData;

import java.util.HashMap;

public interface Clickable extends CustomBox, InteractionSource {
    default void setClickableEffect(OnClick onClick, ClickableEffectData effectData) {
        // Add fields:
        //   private OnClick onClick
        //   private ClickableEffectData clickableEffectData
        // Then:
        //   this.onClick = onClick
        //   this.ClickableEffectData = effectData
    }

    /**
     * Indicates whether this clickable should be processed while the game is paused.
     * Default: false (only explicit UI elements should return true).
     */
    default boolean isPauseUIElement() { return false; }
}
