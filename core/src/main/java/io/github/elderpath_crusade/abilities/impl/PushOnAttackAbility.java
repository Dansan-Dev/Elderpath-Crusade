package io.github.elderpath_crusade.abilities.impl;

import io.github.elderpath_crusade.abilities.AbilityType;
import io.github.elderpath_crusade.abilities.AbilityUtils;
import io.github.elderpath_crusade.abilities.MovementUtils;
import io.github.elderpath_crusade.abilities.TriggeredAbility;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;

/**
 * Charger ability: ON ATTACK: Push the target 1 square backwards and move 1 square towards the target.
 * If a unit or terrain is behind the target, instead only the attacked piece takes 1 damage.
 */
public class PushOnAttackAbility implements TriggeredAbility {
    private MonsterGamePiece owner;

    @Override
    public String getName() { return "Push"; }

    @Override
    public String getDescription() { return getAbilityDescription(); }

    public static String getAbilityDescription() {
        return "ON ATTACK: Push target\n1 back and move 1 forward.\nIf blocked, target takes\n1 damage";
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
    }

    @Override
    public void onOwnerAttack(MonsterGamePiece owner, MonsterGamePiece target, int damage) {
        if (this.owner == null || owner != this.owner) return;
        if (target == null) return;

        // Get owner's position
        Object ownerPosObj = owner.getData(GamePieceData.POSITION);
        if (!(ownerPosObj instanceof Board.Position ownerPos)) return;
        Board board = ownerPos.getBoard();
        if (board == null) return;

        // Get target's position
        Object targetPosObj = target.getData(GamePieceData.POSITION);
        if (!(targetPosObj instanceof Board.Position targetPos)) return;

        int ownerRow = ownerPos.getRow();
        int ownerCol = ownerPos.getCol();
        int targetRow = targetPos.getRow();
        int targetCol = targetPos.getCol();

        // Calculate direction from owner to target
        int rowDir = Integer.compare(targetRow, ownerRow);
        int colDir = Integer.compare(targetCol, ownerCol);

        // Calculate "behind target" position (1 square further in the same direction)
        int behindRow = targetRow + rowDir;
        int behindCol = targetCol + colDir;

        // Check if behind position is valid and empty
        boolean behindValid = (behindRow >= 0 && behindRow < board.getROWS() &&
                              behindCol >= 0 && behindCol < board.getCOLS());
        boolean behindEmpty = behindValid && !board.isOccupied(behindRow, behindCol);

        if (behindEmpty) {
            // Both pieces move: target goes to behind position, owner goes to target's position
            // IMPORTANT: Move owner first, then target, to avoid position conflicts
            // This ensures the target's original position is free when we move the owner there
            // First move owner to target's position (target is still there, but we'll move it next)
            // Actually, we need to move target first to free up the space, then move owner
            // But we need to be careful about the order

            // Move target to behind position first
            boolean targetMoved = MovementUtils.performForcedMovement(
                    board,
                    target,
                    targetRow,
                    targetCol,
                    behindRow,
                    behindCol,
                    "ABILITY",
                    "Push"
            );

            // Only move owner if target move was successful
            if (targetMoved) {
                // Now move owner to target's original position (which is now empty)
                MovementUtils.performForcedMovement(
                        board,
                        owner,
                        ownerRow,
                        ownerCol,
                        targetRow,
                        targetCol,
                        "ABILITY",
                        "Push"
                );
            }
        } else {
            // Blocked: only the attacked piece (target) takes 1 damage
            AbilityUtils.dealDamage(target, 1, owner, true);
            try {
                target.notifyDamaged(1, owner);
            } catch (Exception ignored) {}
        }
    }
}

