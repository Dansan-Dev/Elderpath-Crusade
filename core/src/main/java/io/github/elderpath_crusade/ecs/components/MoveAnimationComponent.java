package io.github.elderpath_crusade.ecs.components;

import com.badlogic.ashley.core.Component;

/**
 * Client-only cosmetic tween (FEAT-013) from one board cell to another, read only by
 * {@code PieceRenderSystem} — never by {@code GridIndexSystem}/{@code PositionComponent}, which
 * stay the instant, authoritative truth the whole time. Used for both a move (pingPong false:
 * glide from {@code fromRow/fromCol} to {@code toRow/toCol} and stay) and an attack lunge
 * (pingPong true: glide out to {@code toRow/toCol} — a point partway toward the defender, not
 * a real board cell — and back to {@code fromRow/fromCol}).
 *
 * <p>{@code client.ViewAnimations} starts a new one mid-flight by reading {@link #shapedProgress()}
 * on whatever's already running, rather than the authoritative replica's (possibly several moves
 * stale) logical position — so a rapid bot sequence (several moves in one tick) continues
 * smoothly from wherever the piece is actually rendered right now instead of snapping backward to
 * replay each intermediate step.
 */
public class MoveAnimationComponent implements Component {
    public static final float DURATION = 0.2f;

    public float fromRow;
    public float fromCol;
    public float toRow;
    public float toCol;
    public float elapsed;
    public boolean pingPong;

    public MoveAnimationComponent set(float fromRow, float fromCol, float toRow, float toCol, boolean pingPong) {
        this.fromRow = fromRow;
        this.fromCol = fromCol;
        this.toRow = toRow;
        this.toCol = toCol;
        this.elapsed = 0f;
        this.pingPong = pingPong;
        return this;
    }

    public boolean isDone() {
        return elapsed >= DURATION;
    }

    /** 0..1, how far through the animation's total duration — before any ping-pong shaping. */
    private float timeProgress() {
        return Math.min(1f, elapsed / DURATION);
    }

    /** The 0..1 fraction to lerp {@code from}->{@code to} by right now — a triangle wave
     *  (0 to 1 to 0) for a ping-pong lunge, otherwise the same as {@link #timeProgress()}. */
    public float shapedProgress() {
        float t = timeProgress();
        return pingPong ? 1f - Math.abs(1f - 2f * t) : t;
    }
}
