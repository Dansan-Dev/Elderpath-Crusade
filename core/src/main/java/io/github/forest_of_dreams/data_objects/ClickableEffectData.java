package io.github.forest_of_dreams.data_objects;

import io.github.forest_of_dreams.enums.ClickableEffectType;
import io.github.forest_of_dreams.enums.ClickableTargetType;
import io.github.forest_of_dreams.utils.Logger;
import lombok.Getter;
import lombok.Setter;

public class ClickableEffectData {
    @Getter private ClickableEffectType type; // PRIORITY
    @Getter private ClickableTargetType targetType;
    @Getter private int extraTargets;
    @Getter @Setter private boolean confirmed = false;

    private ClickableEffectData(ClickableEffectType type, ClickableTargetType targetType, int extraTargets) {
        setData(type, targetType, extraTargets);
    }

    public static ClickableEffectData getImmediate() {
        ClickableEffectData data = new ClickableEffectData(ClickableEffectType.IMMEDIATE, ClickableTargetType.NONE, 0);
        return data;
    }

    public static ClickableEffectData getMulti(ClickableTargetType targetType, int extraTargets) {
        return new ClickableEffectData(ClickableEffectType.MULTI_INTERACTION, targetType, extraTargets);
    }

    public static ClickableEffectData getMultiChoiceLimited(ClickableTargetType targetType, int extraTargets) {
        return new ClickableEffectData(ClickableEffectType.MULTI_CHOICE_LIMITED_INTERACTION, targetType, extraTargets);
    }

    public static ClickableEffectData getMultiChoiceUnlimited(ClickableTargetType targetType) {
        return new ClickableEffectData(ClickableEffectType.MULTI_CHOICE_UNLIMITED_INTERACTION, targetType, 0);
    }

    public void setData(ClickableEffectType type, ClickableTargetType targetType, int extraTargets) {
        if (type == null) {
            Logger.error("ClickableEffectData", "Type cannot be null");
            return;
        }

        switch (type) {
            case IMMEDIATE -> {
                setFields(
                    type,
                    ClickableTargetType.NONE,
                    0
                );
            }
            case MULTI_INTERACTION, MULTI_CHOICE_LIMITED_INTERACTION -> {
                if (extraTargets > 0) {
                    setFields(
                        type,
                        targetType,
                        extraTargets
                    );
                } else Logger.error(
                    "ClickableEffectData",
                    type.name() + " -> Extra targets must be greater than 0"
                );
            }
            case MULTI_CHOICE_UNLIMITED_INTERACTION -> {
                setFields(
                    type,
                    targetType,
                    0
                );
            }
        }
    }

    private void setFields(ClickableEffectType type, ClickableTargetType targetType, int extraTargets) {
        this.type = type;
        this.targetType = targetType;
        this.extraTargets = extraTargets;
    }

    public boolean isValid() {
        return type != null && targetType != null;
    }
}
