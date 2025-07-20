package io.github.forest_of_dreams.interfaces;

import io.github.forest_of_dreams.data_objects.ClickableEffectData;
import io.github.forest_of_dreams.enums.ClickableEffectType;
import io.github.forest_of_dreams.enums.ClickableTargetType;

import java.util.HashMap;
import java.util.List;

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
}
