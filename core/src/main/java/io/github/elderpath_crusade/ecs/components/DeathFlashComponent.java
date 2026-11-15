package io.github.elderpath_crusade.ecs.components;

import com.badlogic.ashley.core.Component;

/**
 * Marks a short-lived, standalone effect entity (FEAT-013's death follow-up) — not a real
 * piece, never added to {@code GridIndexSystem}, carrying no {@code IdentityComponent} — that
 * fades out and then removes itself entirely. {@code client.ViewAnimations} builds one by
 * cloning the real piece's own {@code SpriteComponent}/{@code PositionComponent} snapshot the
 * instant it sees a {@code PieceDiedEvent}, before {@code ReplicaViewSync.sync} removes the
 * real entity that same frame — a "this piece dissolves" effect using the piece's own already-
 * loaded sprite, no new asset needed. Deliberately a separate entity rather than delaying the
 * real one's removal (see {@code elderpath-rendering.md}'s "Client-Only Animations" for why).
 */
public class DeathFlashComponent implements Component {
    public static final float DURATION = 0.3f;

    public float elapsed;

    /** 1 (fully visible) at the start, fading to 0 by {@link #DURATION}. */
    public float alpha() {
        return Math.max(0f, 1f - elapsed / DURATION);
    }

    public boolean isDone() {
        return elapsed >= DURATION;
    }
}
