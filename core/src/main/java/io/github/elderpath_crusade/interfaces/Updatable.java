package io.github.elderpath_crusade.interfaces;

/**
 * Interface for objects that need logic updates every frame.
 */
public interface Updatable {
    /**
     * Update logic.
     * @param delta time since last frame in seconds.
     */
    void update(float delta);
}
