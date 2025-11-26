package io.github.elderpath_crusade.abilities.impl.trigger;

import io.github.elderpath_crusade.abilities.AbilityUtils;
import io.github.elderpath_crusade.abilities.TriggeredAbility;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;

import java.util.ArrayList;
import java.util.List;

/**
 * Barbarian ability: ON ATTACK: Deal damage to all adjacent squares (cleave).
 * After the primary attack damage is dealt, deals damage equal to owner's effective damage
 * to all adjacent MonsterGamePieces (excluding the primary target).
 */
public class CleaveAttackAbility implements TriggeredAbility {
    private MonsterGamePiece owner;

    @Override
    public String getName() { return "Cleave"; }

    @Override
    public String getDescription() { return getAbilityDescription(); }

    public static String getAbilityDescription() {
        return "ON ATTACK: Deal damage to all pieces within 1 range";
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

        // Get owner's position
        Object ownerPosObj = owner.getData(GamePieceData.POSITION);
        if (!(ownerPosObj instanceof Board.Position ownerPos)) return;
        Board board = ownerPos.getBoard();
        if (board == null) return;

        int ownerRow = ownerPos.getRow();
        int ownerCol = ownerPos.getCol();

        // Get target's position
        Object targetPosObj = target.getData(GamePieceData.POSITION);
        if (!(targetPosObj instanceof Board.Position targetPos)) return;
        int targetRow = targetPos.getRow();
        int targetCol = targetPos.getCol();

        // Find all adjacent squares (8 directions: cardinal + diagonal)
        int[][] adjacentDirs = new int[][]{
            {-1, -1}, {-1, 0}, {-1, 1},  // Top row
            {0, -1},           {0, 1},   // Middle row
            {1, -1},  {1, 0},  {1, 1}    // Bottom row
        };

        int cleaveDamage = owner.getEffectiveDamage();
        List<MonsterGamePiece> cleaveTargets = new ArrayList<>();

        for (int[] dir : adjacentDirs) {
            int r = ownerRow + dir[0];
            int c = ownerCol + dir[1];
            if (r < 0 || r >= board.getROWS() || c < 0 || c >= board.getCOLS()) continue;

            GamePiece piece = board.getGamePieceAtPos(r, c);
            if (piece instanceof MonsterGamePiece adjacentTarget) {
                // Exclude the primary target
                if (adjacentTarget == target) continue;
                // Deal cleave damage
                AbilityUtils.dealDamage(adjacentTarget, cleaveDamage, owner, true);
                try {
                    adjacentTarget.notifyDamaged(cleaveDamage, owner);
                } catch (Exception ignored) {}
                cleaveTargets.add(adjacentTarget);
            }
        }

        // Note: The PIECE_ATTACKED event is already emitted by AttackUtils.performAttack()
        // with the primary target. The cleave targets are dealt damage separately.
        // If we need to include them in the event, we would need to modify BaseAttackAbility
        // or AttackUtils to support ability-specific target collection, which is more complex.
    }
}

