package io.github.forest_of_dreams.interfaces;

/**
 * Optional extension for Clickable sources that want to further constrain valid targets
 * beyond the coarse ClickableTargetType. If the active source implements this interface,
 * InteractionManager will consult it for each candidate target during multi-selection.
 */
public interface TargetFilter {
    /**
     * Return true if the given box is an acceptable target for the current effect.
     */
    boolean isValidTargetForEffect(CustomBox box);
}
