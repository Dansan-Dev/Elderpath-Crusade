package io.github.elderpath_crusade.interfaces;

import io.github.elderpath_crusade.game_objects.board.plot.Plot;

import java.util.List;

/**
 * Optional extension for Clickable sources that want to further constrain valid targets
 * beyond the coarse ClickableTargetType. If the active source implements this interface,
 * InteractionManager will consult it for each candidate target during multi-selection.
 */
public interface TargetFilter {
    /**
     * Return true if the given box is an acceptable target for the current effect.
     * @param box The target being validated
     * @param targetIndex The 1-based index of the target being selected (1 = first target, 2 = second target, etc.)
     */
    boolean isValidTargetForEffect(CustomBox box, int targetIndex);

    /**
     * Optionally return a list of eligible plots for visual highlighting.
     * Returns null if this filter doesn't support target highlighting.
     * @param targetIndex The 1-based index of the target being selected (1 = first target, 2 = second target, etc.)
     * @return List of eligible Plot objects, or null if highlighting is not supported
     */
    default List<Plot> getEligibleTargets(int targetIndex) {
        return null;
    }
}
