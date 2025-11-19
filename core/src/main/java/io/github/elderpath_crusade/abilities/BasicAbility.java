package io.github.elderpath_crusade.abilities;

import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.TargetFilter;

import java.util.HashMap;
import java.util.List;

/**
 * Basic abilities are automatically available when a piece is selected.
 * They are not shown in the ability popup, but work through the standard plot click interaction.
 * Examples: BaseMoveAbility, BaseAttackAbility
 */
public interface BasicAbility extends Ability, TargetFilter {
    @Override
    default AbilityType getType() { return AbilityType.BASIC; }

    /**
     * Returns the selection flow for this ability (targets, confirm rules, etc.).
     * Similar to ActionableAbility but for basic interactions.
     */
    ClickableEffectData getClickableEffectData();

    /**
     * Execute with the entities map (0=source, 1..n=targets).
     */
    void execute(HashMap<Integer, CustomBox> entities);

    /**
     * Get eligible targets for highlighting.
     * @param targetIndex The target index (1 for first target, 2 for second, etc.)
     * @return List of eligible Plot targets, or null/empty list if none
     */
    @Override
    List<Plot> getEligibleTargets(int targetIndex);
}

