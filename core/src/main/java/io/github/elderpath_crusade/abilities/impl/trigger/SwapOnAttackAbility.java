package io.github.elderpath_crusade.abilities.impl.trigger;

import io.github.elderpath_crusade.abilities.AbilityType;
import io.github.elderpath_crusade.abilities.MovementUtils;
import io.github.elderpath_crusade.abilities.TriggeredAbility;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;

/**
 * Fairy ability: ON ATTACK: Swap places with the target.
 * Immediately swaps positions with the target when the owner attacks.
 */
public class SwapOnAttackAbility implements TriggeredAbility {
    private MonsterGamePiece owner;

    @Override
    public String getName() { return "Swap"; }

    @Override
    public String getDescription() { return SwapOnAttackAbility.getAbilityDescription(); }

    public static String getAbilityDescription() {
        return "ON ATTACK: Swap places with the target";
    }

    @Override
    public void onAttach(MonsterGamePiece owner) {
        this.owner = owner;
    }

    @Override
    public void onDetach() {
        this.owner = null;
    }

    @Override
    public void onOwnerAttack(MonsterGamePiece owner, MonsterGamePiece target, int damage) {
        if (this.owner == null || owner != this.owner) return;
        if (target == null) return;

        // Get current positions
        Object ownerPosObj = owner.getData(GamePieceData.POSITION);
        Object targetPosObj = target.getData(GamePieceData.POSITION);

        if (!(ownerPosObj instanceof Board.Position ownerPos) || !(targetPosObj instanceof Board.Position targetPos)) {
            return;
        }

        Board board = ownerPos.getBoard();
        if (board == null || board != targetPos.getBoard()) {
            return; // Must be on the same board
        }

        int ownerRow = ownerPos.getRow();
        int ownerCol = ownerPos.getCol();
        int targetRow = targetPos.getRow();
        int targetCol = targetPos.getCol();

        // Perform the swap immediately (both pieces move simultaneously)
        MovementUtils.performSwap(
                board,
                owner,
                ownerRow,
                ownerCol,
                target,
                targetRow,
                targetCol,
                "ABILITY",
                "Swap"
        );
    }
}

