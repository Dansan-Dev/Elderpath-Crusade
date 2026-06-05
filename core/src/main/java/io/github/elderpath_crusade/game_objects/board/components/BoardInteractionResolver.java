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
            Entity entity = board.getEntityAtPos(src.getRow(), src.getCol());
            if (entity == null) return;

            PieceAlignment alignment = EntityUtils.getAlignment(entity);
            if (alignment != GameContext.get().getTurnManager().getCurrentPlayer()) return;
            if (EntityUtils.isStunned(entity) || EntityUtils.isExhausted(entity)) return;

            Entity targetEntity = board.getEntityAtPos(dst.getRow(), dst.getCol());
            if (targetEntity != null && EntityUtils.getAlignment(targetEntity) != alignment) {
                // Attack via ECS
                AttackSystem attackSystem = GameContext.get().getEcsEngine().getSystem(AttackSystem.class);
                if (attackSystem != null) {
                    boolean success = attackSystem.executeAttack(entity, dst.getRow(), dst.getCol());
                    if (success) spendAction(entity);
                }
            } else if (targetEntity == null) {
                // Move via ECS
                MovementSystem movementSystem = GameContext.get().getEcsEngine().getSystem(MovementSystem.class);
                if (movementSystem != null) {
                    boolean success = movementSystem.executeMove(entity, dst.getRow(), dst.getCol());
                    if (success) spendAction(entity);
                }
            }
        }
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
