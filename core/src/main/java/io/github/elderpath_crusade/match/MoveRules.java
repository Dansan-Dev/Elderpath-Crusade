package io.github.elderpath_crusade.match;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.ecs.systems.AttackSystem;
import io.github.elderpath_crusade.ecs.systems.MovementSystem;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.ActionSpentEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.match.ActionResult.Reason;

/**
 * Authoritative move/attack by board coordinates, on the active match's BoardGrid — headless,
 * so local clicks, bots and network commands share it (via GameServer).
 *
 * validate() is the single definition of a legal move/attack: the click layer's target check
 * (Plot.isValidTargetForEffect) uses it too, and destinations come from the same
 * BoardGrid.getReachable / getAttackableEnemies calls the highlights use, so what the UI
 * offers and what the server accepts can't diverge.
 */
public final class MoveRules {
    private MoveRules() {}

    /** Whether actor may move the piece at src to the empty dst, or attack the enemy at dst — without doing it. */
    public static ActionResult validate(PieceAlignment actor, int srcRow, int srcCol, int dstRow, int dstCol) {
        BoardGrid grid = GameContext.get().getActiveGrid();
        if (grid == null) return ActionResult.rejected(Reason.NO_ACTIVE_MATCH);
        if (!grid.isInBounds(srcRow, srcCol) || !grid.isInBounds(dstRow, dstCol)) {
            return ActionResult.rejected(Reason.OUT_OF_BOUNDS);
        }
        if (actor == null || actor != GameContext.get().getTurnManager().getCurrentPlayer()) {
            return ActionResult.rejected(Reason.NOT_YOUR_TURN);
        }

        Entity entity = grid.getEntityAt(srcRow, srcCol);
        if (entity == null || entity.getComponent(StatsComponent.class) == null) {
            return ActionResult.rejected(Reason.NO_PIECE);
        }
        if (EntityUtils.getAlignment(entity) != actor) return ActionResult.rejected(Reason.NOT_YOUR_PIECE);
        if (EntityUtils.isStunned(entity)) return ActionResult.rejected(Reason.PIECE_STUNNED);
        if (EntityUtils.isExhausted(entity)) return ActionResult.rejected(Reason.PIECE_EXHAUSTED);

        Coord dst = new Coord(dstRow, dstCol);
        Entity target = grid.getEntityAt(dstRow, dstCol);
        if (target == null) {
            int speed = EntityUtils.getSpeed(entity);
            return grid.getReachable(entity, srcRow, srcCol, speed).contains(dst)
                    ? ActionResult.ok() : ActionResult.rejected(Reason.UNREACHABLE);
        }
        if (!isEnemyPiece(target, actor)) return ActionResult.rejected(Reason.INVALID_TARGET);
        if (!grid.getAttackableEnemies(srcRow, srcCol, actor).contains(dst)) {
            return ActionResult.rejected(Reason.OUT_OF_RANGE);
        }
        if (!EntityUtils.canAttack(entity)) return ActionResult.rejected(Reason.ATTACK_NOT_ALLOWED);
        return ActionResult.ok();
    }

    /** Validates, then moves the piece at src to an empty dst or attacks the enemy at dst, spending one action. */
    public static ActionResult moveOrAttack(PieceAlignment actor, int srcRow, int srcCol, int dstRow, int dstCol) {
        ActionResult validation = validate(actor, srcRow, srcCol, dstRow, dstCol);
        if (!validation.isOk()) return validation;

        BoardGrid grid = GameContext.get().getActiveGrid();
        Entity entity = grid.getEntityAt(srcRow, srcCol);
        boolean attack = grid.getEntityAt(dstRow, dstCol) != null;
        boolean success;
        if (attack) {
            AttackSystem attackSystem = GameContext.get().getEcsEngine().getSystem(AttackSystem.class);
            // AttackSystem also enforces once-per-turn attack restrictions (abilities)
            success = attackSystem != null && attackSystem.executeAttack(entity, dstRow, dstCol);
            if (!success) return ActionResult.rejected(Reason.ATTACK_NOT_ALLOWED);
        } else {
            MovementSystem movementSystem = GameContext.get().getEcsEngine().getSystem(MovementSystem.class);
            success = movementSystem != null && movementSystem.executeMove(entity, dstRow, dstCol);
            if (!success) return ActionResult.rejected(Reason.EXECUTION_FAILED);
        }
        spendAction(entity);
        return ActionResult.ok();
    }

    private static boolean isEnemyPiece(Entity target, PieceAlignment actor) {
        if (target.getComponent(StatsComponent.class) == null) return false; // terrain
        PieceAlignment alignment = EntityUtils.getAlignment(target);
        return alignment != null && alignment != actor && alignment != PieceAlignment.NEUTRAL;
    }

    private static void spendAction(Entity entity) {
        StatsComponent stats = entity.getComponent(StatsComponent.class);
        if (stats == null) return;
        int left = Math.max(0, stats.remainingActions - 1);
        stats.remainingActions = left;
        TypedEventBus.get().emit(new ActionSpentEvent(
                EntityUtils.getId(entity), EntityUtils.getAlignment(entity), left));
    }
}
