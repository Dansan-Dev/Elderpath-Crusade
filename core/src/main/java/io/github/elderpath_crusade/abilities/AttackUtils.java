package io.github.elderpath_crusade.abilities;

import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.multiplayer.EventBus;
import io.github.elderpath_crusade.multiplayer.GameEventType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility class for attack operations.
 * Handles damage, notifications, death, and event emission.
 */
public final class AttackUtils {
    private AttackUtils() {}

    /**
     * Performs an attack from attacker to defender.
     * Handles damage, notifications, death, and emits PIECE_ATTACKED event.
     * 
     * @param board The board where the attack occurs
     * @param attacker The attacking piece
     * @param defender The defending piece
     * @param attackerRow Row of the attacker
     * @param attackerCol Column of the attacker
     * @param defenderRow Row of the defender
     * @param defenderCol Column of the defender
     * @param abilityName Name of the ability causing the attack (null for base attack)
     * @param additionalTargets List of additional target piece IDs (for AoE attacks like cleave)
     * @return true if the attack was successful, false otherwise
     */
    public static boolean performAttack(
            Board board,
            MonsterGamePiece attacker,
            MonsterGamePiece defender,
            int attackerRow,
            int attackerCol,
            int defenderRow,
            int defenderCol,
            String abilityName,
            List<String> additionalTargets
    ) {
        if (board == null || attacker == null || defender == null) return false;
        if (attacker == defender) return false;

        // Damage amount uses effective stats of the attacker
        int dmg = attacker.getEffectiveDamage();

        // Notify hooks first (mirrors existing patterns where appropriate)
        try { attacker.notifyAttack(defender, dmg); } catch (Exception ignored) {}
        try { defender.notifyDamaged(dmg, attacker); } catch (Exception ignored) {}

        // Deal damage
        defender.getStats().dealDamage(dmg);

        // Build targets list (primary target + additional targets)
        List<String> allTargets = new ArrayList<>();
        allTargets.add(defender.getId().toString());
        if (additionalTargets != null) {
            allTargets.addAll(additionalTargets);
        }

        // Emit attack event with ability reference and all targets
        Map<String, Object> eventData = new java.util.HashMap<>();
        eventData.put("attackerId", attacker.getId().toString());
        eventData.put("defenderId", defender.getId().toString());
        eventData.put("row", defenderRow);
        eventData.put("col", defenderCol);
        eventData.put("attackerRow", attackerRow);
        eventData.put("attackerCol", attackerCol);
        eventData.put("defenderRow", defenderRow);
        eventData.put("defenderCol", defenderCol);
        eventData.put("damage", dmg);
        eventData.put("targets", allTargets);
        if (abilityName != null) {
            eventData.put("ability", abilityName);
        }

        EventBus.emit(GameEventType.PIECE_ATTACKED, eventData);

        // Handle death
        if (defender.getStats().getCurrentHealth() <= 0) {
            try { defender.notifyDied(); } catch (Exception ignored) {}
            board.removeGamePieceAtPos(defenderRow, defenderCol);
            EventBus.emit(
                    GameEventType.PIECE_DIED,
                    Map.of(
                            "pieceId", defender.getId().toString(),
                            "row", defenderRow,
                            "col", defenderCol
                    )
            );
        }

        return true;
    }

    /**
     * Convenience method for base attack (no ability, no additional targets).
     */
    public static boolean performAttack(
            Board board,
            MonsterGamePiece attacker,
            MonsterGamePiece defender,
            int attackerRow,
            int attackerCol,
            int defenderRow,
            int defenderCol
    ) {
        return performAttack(board, attacker, defender, attackerRow, attackerCol, defenderRow, defenderCol, null, null);
    }
}

