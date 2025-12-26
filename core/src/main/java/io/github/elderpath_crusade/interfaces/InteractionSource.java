package io.github.elderpath_crusade.interfaces;

import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.game_objects.board.plot.Plot;

import java.util.HashMap;
import java.util.List;

/**
 * Defines the logic for an entity that can initiate a selection-based
 * interaction.
 * Separates the "what it does" (Logic) from the "where it is" (UI/Clickable).
 */
public interface InteractionSource extends CustomBox, TargetFilter {
    /**
     * Returns the metadata defining the selection flow (target type, count, etc.).
     */
    ClickableEffectData getClickableEffectData();

    /**
     * Invoked when the selection is complete and confirmed.
     *
     * @param entities The selected entities (0=source, 1..n=targets).
     */
    void triggerClickEffect(HashMap<Integer, CustomBox> entities);

    /**
     * Optional semantic validation for a target.
     * Defaults to true (allow all targets matching the ClickableEffectData type).
     */
    default boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
        return true;
    }

    /**
     * Optional list of eligible plots for UI highlighting.
     * Defaults to null (no special highlights beyond type-based defaults).
     */
    default List<Plot> getEligibleTargets(int targetIndex) {
        return null;
    }

    // Default CustomBox implementation for programmatic sources that don't have
    // physical bounds
    @Override
    default int getX() {
        return 0;
    }

    @Override
    default int getY() {
        return 0;
    }

    @Override
    default int getWidth() {
        return 0;
    }

    @Override
    default int getHeight() {
        return 0;
    }
}
