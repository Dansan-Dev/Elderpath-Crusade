package io.github.elderpath_crusade.game_objects.board.components;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.ecs.systems.AttackSystem;
import io.github.elderpath_crusade.ecs.systems.MovementSystem;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.ActionSpentEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;

import java.util.HashMap;

/**
 * Orchestrates the resolution of board interactions (plot clicks) into
 * movement or attack actions via ECS systems.
 */
public class BoardInteractionResolver {
    private final Board board;

    public BoardInteractionResolver(Board board) {
        this.board = board;
    }

    public void handlePlotMove(HashMap<Integer, CustomBox> entities) {
        if (entities.get(0) instanceof Plot src && entities.get(1) instanceof Plot dst) {
            movePlot(src.getRow(), src.getCol(), dst.getRow(), dst.getCol());
        }
    }

    /**
     * Authoritative move/attack execution keyed by board coordinates rather than live
     * Plot objects, so it can be called identically from a local click (see
     * handlePlotMove) or a network command (see GameServer) — the source of truth for
     * "what happens when you move from A to B" lives here, once.
     */
    public boolean movePlot(int srcRow, int srcCol, int dstRow, int dstCol) {
        Entity entity = board.getEntityAtPos(srcRow, srcCol);
        if (entity == null) return false;

        PieceAlignment alignment = EntityUtils.getAlignment(entity);
        if (alignment != GameContext.get().getTurnManager().getCurrentPlayer()) return false;
        if (EntityUtils.isStunned(entity) || EntityUtils.isExhausted(entity)) return false;

        Entity targetEntity = board.getEntityAtPos(dstRow, dstCol);
        if (targetEntity != null && EntityUtils.getAlignment(targetEntity) != alignment) {
            // Attack via ECS
            AttackSystem attackSystem = GameContext.get().getEcsEngine().getSystem(AttackSystem.class);
            if (attackSystem == null) return false;
            boolean success = attackSystem.executeAttack(entity, dstRow, dstCol);
            if (success) spendAction(entity);
            return success;
        } else if (targetEntity == null) {
            // Move via ECS
            MovementSystem movementSystem = GameContext.get().getEcsEngine().getSystem(MovementSystem.class);
            if (movementSystem == null) return false;
            boolean success = movementSystem.executeMove(entity, dstRow, dstCol);
            if (success) spendAction(entity);
            return success;
        }
        return false;
    }

    private void spendAction(Entity entity) {
        io.github.elderpath_crusade.ecs.components.StatsComponent stats =
                entity.getComponent(io.github.elderpath_crusade.ecs.components.StatsComponent.class);
        if (stats == null) return;
        int left = Math.max(0, stats.remainingActions - 1);
        stats.remainingActions = left;
        TypedEventBus.get().emit(new ActionSpentEvent(
                EntityUtils.getId(entity), EntityUtils.getAlignment(entity), left));
    }
}
