package io.github.elderpath_crusade.ecs.components;

import com.badlogic.ashley.core.Component;

/**
 * Client-only cosmetic fade-in (FEAT-013) for a just-spawned piece — {@code PieceRenderSystem}
 * multiplies the sprite's alpha by {@link #alpha()} while this is present, so a new summon
 * appears over {@link #DURATION} seconds instead of popping in at full opacity instantly.
 */
public class SpawnAnimationComponent implements Component {
    public static final float DURATION = 0.3f;

    public float elapsed;

    public float alpha() {
        return Math.min(1f, elapsed / DURATION);
    }

    public boolean isDone() {
        return elapsed >= DURATION;
    }
}
