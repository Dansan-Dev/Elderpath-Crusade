package io.github.forest_of_dreams.interfaces;

import io.github.forest_of_dreams.data_objects.ClickableEffectData;

import java.util.HashMap;

public interface Clickable extends CustomBox {
    default void setClickableEffect(OnClick onClick, ClickableEffectData effectData) {
        // Add fields:
        //   private OnClick onClick
        //   private ClickableEffectData clickableEffectData
        // Then:
        //   this.onClick = onClick
        //   this.ClickableEffectData = effectData
    }
    default ClickableEffectData getClickableEffectData() {

        return null; // clickableEffectData
    };
    default void triggerClickEffect(HashMap<Integer, CustomBox> interactionEntities) {
        // onClick.run(interactionEntities);
    };

    /**
     * Indicates whether this clickable should be processed while the game is paused.
     * Default: false (only explicit UI elements should return true).
     */
    default boolean isPauseUIElement() { return false; }
}
