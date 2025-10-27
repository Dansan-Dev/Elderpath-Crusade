package io.github.forest_of_dreams.abilities;

import io.github.forest_of_dreams.data_objects.ClickableEffectData;
import io.github.forest_of_dreams.interfaces.CustomBox;
import java.util.HashMap;

/**
 * Actionable abilities expose a user-invoked interaction that consumes an action when executed.
 * UI wiring and action spending will be handled by the caller (Board/Interaction system) in later chunks.
 */
public interface ActionableAbility extends Ability {
    @Override
    default AbilityType getType() { return AbilityType.ACTIONABLE; }

    /** Returns the selection flow for this ability (targets, confirm rules, etc.). */
    ClickableEffectData getClickableEffectData();

    /** Execute with the entities map (0=source, 1..n=targets). Return true on success. */
    boolean execute(HashMap<Integer, CustomBox> entities);
}
