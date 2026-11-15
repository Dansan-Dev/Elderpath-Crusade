package io.github.elderpath_crusade.client;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.components.DeathFlashComponent;
import io.github.elderpath_crusade.ecs.components.MoveAnimationComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.SpawnAnimationComponent;
import io.github.elderpath_crusade.ecs.components.SpriteComponent;
import io.github.elderpath_crusade.events.GameEvent;
import io.github.elderpath_crusade.events.PieceAttackedEvent;
import io.github.elderpath_crusade.events.PieceDiedEvent;
import io.github.elderpath_crusade.events.PieceMovedEvent;
import io.github.elderpath_crusade.events.PieceSpawnedEvent;

/**
 * Turns the replica's event stream into cosmetic view-entity animations (FEAT-013): a piece
 * glides between cells on a move, an attacker lunges toward its defender and back, a new summon
 * fades in, and a dying piece's last look fades out. Registered via {@code ClientMatchState
 * .addEventListener} alongside {@code BattleRoom.wireHandSync} (that widening, FEAT-017's
 * prerequisite, is exactly what lets this coexist with hand sync on the same per-seat state).
 *
 * <p>Death is handled without ever delaying the real piece's removal — the client despawns its
 * entity the instant it drops out of {@code ClientMatchState.getPieces()} ({@code
 * ReplicaViewSync.sync}'s removal phase), and holding onto it longer would mean restructuring
 * the exact two-phase vacate-then-claim ordering {@code ReplicaViewSync} already had to get
 * right once for a simultaneous swap (BUG-050) — too big a risk to take blind in the same pass
 * as everything else here. Instead, {@link #animateDeath} clones the dying entity's own, already-
 * loaded sprite into a brand-new, standalone effect entity (no {@code IdentityComponent}, never
 * added to {@code GridIndexSystem}) the instant the event arrives — before {@code sync} removes
 * the real one this same frame — and lets it fade out on its own. {@code PieceRenderSystem}
 * picks it up automatically (it only requires {@code SpriteComponent}+{@code PositionComponent},
 * nothing piece-specific), and it's exempt from the stun/exhaustion tint checks there.
 *
 * <p>Holds no game rules and never touches the real {@code PositionComponent}/
 * {@code GridIndexSystem} — purely additive components {@code PieceRenderSystem} reads for
 * where/how to draw a sprite, plus one standalone, self-removing effect entity for death.
 */
public final class ViewAnimations {
    /** How far (0..1) an attack lunge travels toward the defender before returning. */
    private static final float LUNGE_FRACTION = 0.35f;

    private final ReplicaViewSync viewSync;

    public ViewAnimations(ReplicaViewSync viewSync) {
        this.viewSync = viewSync;
    }

    public void onEvent(GameEvent event) {
        if (event instanceof PieceMovedEvent e) {
            animateMove(e.pieceId(), e.fromRow(), e.fromCol(), e.toRow(), e.toCol());
        } else if (event instanceof PieceAttackedEvent e) {
            animateLunge(e.attackerId(), e.attackerRow(), e.attackerCol(), e.defenderRow(), e.defenderCol());
        } else if (event instanceof PieceSpawnedEvent e) {
            animateSpawn(e.pieceId());
        } else if (event instanceof PieceDiedEvent e) {
            animateDeath(e.pieceId(), e.row(), e.col());
        }
    }

    private void animateMove(String pieceId, int fromRow, int fromCol, int toRow, int toCol) {
        Entity entity = viewSync.getEntity(pieceId);
        if (entity == null) return;
        float[] visualFrom = currentVisualPosition(entity, fromRow, fromCol);
        animOf(entity).set(visualFrom[0], visualFrom[1], toRow, toCol, false);
    }

    private void animateLunge(String attackerId, int attackerRow, int attackerCol, int defenderRow, int defenderCol) {
        Entity entity = viewSync.getEntity(attackerId);
        if (entity == null) return;
        // A ping-pong animation returns to "from" at the end, so the real board cell (not the
        // lunge's own mid-flight start, which only matters if a lunge interrupts another
        // animation) belongs in "from" here — an attacker never needs to glide back from
        // somewhere else, it's standing still on its own cell both before and after.
        float midRow = attackerRow + (defenderRow - attackerRow) * LUNGE_FRACTION;
        float midCol = attackerCol + (defenderCol - attackerCol) * LUNGE_FRACTION;
        animOf(entity).set(attackerRow, attackerCol, midRow, midCol, true);
    }

    private void animateSpawn(String pieceId) {
        Entity entity = viewSync.getEntity(pieceId);
        if (entity == null) return;
        if (entity.getComponent(SpawnAnimationComponent.class) == null) {
            entity.add(new SpawnAnimationComponent());
        }
    }

    /**
     * Called while the dying piece's own entity still exists this frame (this listener fires
     * during {@code session.poll()}, before {@code ReplicaViewSync.sync} removes it) — reads
     * its current sprite and visual position, then builds a separate, standalone effect entity
     * that outlives it and fades out on its own.
     */
    private void animateDeath(String pieceId, int row, int col) {
        Entity dying = viewSync.getEntity(pieceId);
        if (dying == null) return;
        SpriteComponent sprite = dying.getComponent(SpriteComponent.class);
        if (sprite == null || sprite.renderable == null) return;

        float[] visualAt = currentVisualPosition(dying, row, col);

        Engine engine = GameContext.get().getEcsEngine();
        Entity flash = engine.createEntity();
        flash.add(new SpriteComponent().setRenderable(sprite.renderable));
        flash.add(new PositionComponent().set(Math.round(visualAt[0]), Math.round(visualAt[1])));
        flash.add(new DeathFlashComponent());
        engine.addEntity(flash);
    }

    private static MoveAnimationComponent animOf(Entity entity) {
        MoveAnimationComponent anim = entity.getComponent(MoveAnimationComponent.class);
        if (anim == null) {
            anim = new MoveAnimationComponent();
            entity.add(anim);
        }
        return anim;
    }

    /**
     * Where the piece is actually rendered right now — the tail of any still-running animation,
     * or the given logical cell if none is running — so a new move started mid-animation (a
     * rapid bot sequence) continues smoothly from there instead of snapping back to replay the
     * intermediate step the event itself reports as its "from".
     */
    private static float[] currentVisualPosition(Entity entity, int fallbackRow, int fallbackCol) {
        MoveAnimationComponent existing = entity.getComponent(MoveAnimationComponent.class);
        if (existing == null) return new float[] { fallbackRow, fallbackCol };
        float t = existing.shapedProgress();
        return new float[] {
                lerp(existing.fromRow, existing.toRow, t),
                lerp(existing.fromCol, existing.toCol, t)
        };
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
