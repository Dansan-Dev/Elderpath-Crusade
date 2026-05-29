package io.github.elderpath_crusade.game_objects.board.components;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.abilities.Ability;
import io.github.elderpath_crusade.abilities.BasicAbility;
import io.github.elderpath_crusade.abilities.impl._base.BaseAttackAbility;
import io.github.elderpath_crusade.abilities.impl._base.BaseMoveAbility;
import io.github.elderpath_crusade.abilities.impl._base_override.JumpMoveAbility;
import io.github.elderpath_crusade.abilities.impl._base_override.OncePerTurnAttackAbility;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.managers.TurnManager;

import java.util.HashMap;

/**
 * Orchestrates the resolution of board interactions (e.g., plot clicks) into
 * specific game actions.
 */
public class BoardInteractionResolver {
    private final Board board;

    public BoardInteractionResolver(Board board) {
        this.board = board;
    }

    /**
     * Resolves a multi-entity interaction (typically a click on a plot) into a
     * movement or attack action.
     *
     * @param entities The entities involved in the interaction (0=source,
     *                 1=target).
     */
    public void handlePlotMove(HashMap<Integer, CustomBox> entities) {
        if (entities.get(0) instanceof Plot src && entities.get(1) instanceof Plot dst) {
            GamePiece gp = board.getGamePieceAtPos(src.getRow(), src.getCol());
            if (gp instanceof MonsterGamePiece mgp) {
                // Ensure it's the current player's piece
                if (mgp.getAlignment() != GameContext.get().getTurnManager().getCurrentPlayer())
                    return;

                // Stunned or exhausted pieces cannot act
                if (mgp.isStunned() || mgp.isExhausted())
                    return;

                GamePiece targetPiece = board.getGamePieceAtPos(dst.getRow(), dst.getCol());
                if (targetPiece instanceof MonsterGamePiece enemy && enemy.getAlignment() != mgp.getAlignment()) {
                    // Attack Ability selection
                    BasicAbility attack = getAbilityByClass(mgp, OncePerTurnAttackAbility.class);
                    if (attack == null)
                        attack = getAbilityByClass(mgp, BaseAttackAbility.class);

                    if (attack != null) {
                        attack.execute(entities);
                    }
                } else if (targetPiece == null) {
                    // Move Ability selection
                    BasicAbility move = getAbilityByClass(mgp, JumpMoveAbility.class);
                    if (move == null)
                        move = getAbilityByClass(mgp, BaseMoveAbility.class);

                    if (move != null) {
                        move.execute(entities);
                    }
                }
            }
        }
    }

    /**
     * Helper to find a basic ability of a specific class on a piece.
     */
    @SuppressWarnings("unchecked")
    private <T extends BasicAbility> T getAbilityByClass(MonsterGamePiece piece, Class<T> clazz) {
        for (Ability a : piece.getAbilities()) {
            if (clazz.isInstance(a))
                return (T) a;
        }
        return null;
    }
}
