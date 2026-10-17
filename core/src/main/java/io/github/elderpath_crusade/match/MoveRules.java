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

/**
 * Authoritative move/attack by board coordinates, on the active match's BoardGrid — headless,
 * so local clicks, bots and network commands share it (via GameServer).
 */
public final class MoveRules {
    private MoveRules() {}

    /** Moves the piece at src to an empty dst, or attacks the enemy at dst. Returns true on success. */
    public static boolean moveOrAttack(int srcRow, int srcCol, int dstRow, int dstCol) {
        BoardGrid grid = GameContext.get().getActiveGrid();
        if (grid == null || !grid.isInBounds(srcRow, srcCol) || !grid.isInBounds(dstRow, dstCol)) return false;
        Entity entity = grid.getEntityAt(srcRow, srcCol);
        if (entity == null) return false;

        PieceAlignment alignment = EntityUtils.getAlignment(entity);
        if (alignment != GameContext.get().getTurnManager().getCurrentPlayer()) return false;
        if (EntityUtils.isStunned(entity) || EntityUtils.isExhausted(entity)) return false;

        Entity targetEntity = grid.getEntityAt(dstRow, dstCol);
        if (targetEntity != null && EntityUtils.getAlignment(targetEntity) != alignment) {
            AttackSystem attackSystem = GameContext.get().getEcsEngine().getSystem(AttackSystem.class);
            if (attackSystem == null) return false;
            boolean success = attackSystem.executeAttack(entity, dstRow, dstCol);
            if (success) spendAction(entity);
            return success;
        } else if (targetEntity == null) {
            MovementSystem movementSystem = GameContext.get().getEcsEngine().getSystem(MovementSystem.class);
            if (movementSystem == null) return false;
            boolean success = movementSystem.executeMove(entity, dstRow, dstCol);
            if (success) spendAction(entity);
            return success;
        }
        return false;
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
