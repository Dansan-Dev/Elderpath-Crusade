package io.github.elderpath_crusade.abilities.impl;

import io.github.elderpath_crusade.abilities.AbilityType;
import io.github.elderpath_crusade.abilities.MovementUtils;
import io.github.elderpath_crusade.abilities.TriggeredAbility;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.multiplayer.GameEvent;
import io.github.elderpath_crusade.multiplayer.GameEventType;

import java.util.Map;

/**
 * Fairy ability: ON ATTACK: Swap places with the target.
 * When the owner attacks an enemy that dies, move to the target's position after it dies.
 */
public class SwapOnAttackAbility implements TriggeredAbility {
    private MonsterGamePiece owner;
    // Track the target of the current attack (cleared after swap or new attack)
    private String trackedTargetId;
    // Store the target's position for when it dies
    private int targetRow;
    private int targetCol;
    private Board targetBoard;

    @Override
    public String getName() { return "Swap"; }

    @Override
    public String getDescription() { return SwapOnAttackAbility.getAbilityDescription(); }

    public static String getAbilityDescription() {
        return "ON ATTACK: Swap places\nwith the target";
    }

    @Override
    public AbilityType getType() { return AbilityType.TRIGGERED; }

    @Override
    public void onAttach(MonsterGamePiece owner) {
        this.owner = owner;
    }

    @Override
    public void onDetach() {
        this.owner = null;
        clearTracking();
    }

    @Override
    public void onOwnerAttack(MonsterGamePiece owner, MonsterGamePiece target, int damage) {
        if (this.owner == null || owner != this.owner) return;
        if (target == null) return;

        // Clear any previous tracking (new attack sequence starting)
        clearTracking();

        // Track the target and its position for potential swap after death
        Object targetPosObj = target.getData(GamePieceData.POSITION);
        if (!(targetPosObj instanceof Board.Position targetPos)) return;

        Board board = targetPos.getBoard();
        if (board == null) return;

        trackedTargetId = target.getId().toString();
        targetRow = targetPos.getRow();
        targetCol = targetPos.getCol();
        targetBoard = board;
    }

    @Override
    public void onGameEvent(GameEvent event) {
        if (owner == null) return;
        GameEventType type = event.getType();

        // Check PIECE_DIED events to see if our tracked target died
        if (type == GameEventType.PIECE_DIED) {
            String deadPieceId = (String) event.getData().get("pieceId");

            // Only process if the dead piece matches our tracked target
            if (trackedTargetId != null
                && trackedTargetId.equals(deadPieceId)
                && targetBoard != null) {

                // Get owner's current position
                Object ownerPosObj = owner.getData(GamePieceData.POSITION);
                if (!(ownerPosObj instanceof Board.Position ownerPos)) {
                    clearTracking();
                    return;
                }

                Board board = ownerPos.getBoard();
                if (board == null || board != targetBoard) {
                    clearTracking();
                    return;
                }

                int ownerRow = ownerPos.getRow();
                int ownerCol = ownerPos.getCol();

                // Move Fairy to the target's position (target is already dead and removed)
                // The position should now be empty
                if (board.getGamePieceAtPos(targetRow, targetCol) == null) {
                    MovementUtils.performForcedMovement(
                            board,
                            owner,
                            ownerRow,
                            ownerCol,
                            targetRow,
                            targetCol,
                            "ABILITY",
                            "Swap"
                    );
                }
            }

            // Clear tracking after processing death (whether it was our target or not)
            clearTracking();
        }
    }

    /**
     * Clear the tracked target. Called after swap completes or new attack starts.
     */
    private void clearTracking() {
        trackedTargetId = null;
        targetRow = -1;
        targetCol = -1;
        targetBoard = null;
    }
}

