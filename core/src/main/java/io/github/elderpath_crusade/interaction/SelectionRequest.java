package io.github.elderpath_crusade.interaction;

import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.InteractionSource;

import java.util.HashMap;
import java.util.function.Consumer;

/**
 * Describes what a selection flow needs: source, target count, and completion callback.
 */
public record SelectionRequest(
        InteractionSource source,
        int requiredTargets,
        boolean requiresConfirmation,
        Consumer<HashMap<Integer, CustomBox>> onComplete
) {
    /**
     * Create a simple request for N targets with auto-complete (no confirmation needed).
     */
    public static SelectionRequest targets(InteractionSource source, int count, Consumer<HashMap<Integer, CustomBox>> onComplete) {
        return new SelectionRequest(source, count, false, onComplete);
    }

    /**
     * Create a request that requires explicit confirmation (for variable-count selections).
     */
    public static SelectionRequest withConfirmation(InteractionSource source, int maxTargets, Consumer<HashMap<Integer, CustomBox>> onComplete) {
        return new SelectionRequest(source, maxTargets, true, onComplete);
    }
}
