package io.github.elderpath_crusade.game_objects.board.components;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.systems.AttackSystem;
import io.github.elderpath_crusade.ecs.systems.MovementSystem;
import io.github.elderpath_crusade.events.ActionSpentEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
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
            GamePiece gp = board.getGamePieceAtPos(src.getRow(), src.getCol());
            if (!(gp instanceof MonsterGamePiece mgp)) return;

            if (mgp.getAlignment() != GameContext.get().getTurnManager().getCurrentPlayer()) return;
            if (mgp.isStunned() || mgp.isExhausted()) return;
            if (mgp.getEntity() == null) return;

            GamePiece targetPiece = board.getGamePieceAtPos(dst.getRow(), dst.getCol());
            if (targetPiece instanceof MonsterGamePiece enemy && enemy.getAlignment() != mgp.getAlignment()) {
                // Attack via ECS
                AttackSystem attackSystem = GameContext.get().getEcsEngine().getSystem(AttackSystem.class);
                if (attackSystem != null) {
                    boolean success = attackSystem.executeAttack(mgp.getEntity(), dst.getRow(), dst.getCol());
                    if (success) spendAction(mgp);
                }
            } else if (targetPiece == null) {
                // Move via ECS
                MovementSystem movementSystem = GameContext.get().getEcsEngine().getSystem(MovementSystem.class);
                if (movementSystem != null) {
                    boolean success = movementSystem.executeMove(mgp.getEntity(), dst.getRow(), dst.getCol());
                    if (success) spendAction(mgp);
                }
            }
        }
    }

    private void spendAction(MonsterGamePiece mgp) {
        int left = Math.max(0, mgp.getStats().getRemainingActions() - 1);
        mgp.getStats().setRemainingActions(left);
        TypedEventBus.get().emit(new ActionSpentEvent(
                mgp.getId().toString(), mgp.getAlignment(), left));
    }
}
