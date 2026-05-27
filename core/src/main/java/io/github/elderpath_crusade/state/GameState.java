package io.github.elderpath_crusade.state;

import io.github.elderpath_crusade.GameContext;

/**
 * A game state with explicit lifecycle. States own their resources
 * and clean up on exit.
 */
public interface GameState {
    /** Called when this state becomes active. Set up scene, UI, listeners. */
    void enter(GameContext context);

    /** Called every frame. Handle logic updates. */
    void update(float delta);

    /** Called when transitioning away. Clean up resources, unregister listeners. */
    void exit();

    /** Called on screen resize. Recompute layouts. */
    default void resize(int width, int height) {}
}
