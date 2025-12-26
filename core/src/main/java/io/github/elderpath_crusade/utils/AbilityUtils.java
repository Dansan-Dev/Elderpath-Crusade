package io.github.elderpath_crusade.utils;

import io.github.elderpath_crusade.abilities.ActionableAbility;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.game_piece.MonsterGamePiece;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.multiplayer.EventBus;
import io.github.elderpath_crusade.multiplayer.GameEventType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Small static helpers for common ability-side operations.
 * Returns boolean value for success/failure.
 */
public final class AbilityUtils {
    /**
     * Performs an attack from attacker to defender.
     * Handles damage, notifications, death, and emits PIECE_ATTACKED event.
     *
     * @param board             The board where the attack occurs
     * @param attacker          The attacking piece
     * @param defender          The defending piece
     * @param attackerRow       Row of the attacker
     * @param attackerCol       Column of the attacker
     * @param defenderRow       Row of the defender
     * @param defenderCol       Column of the defender
     * @param abilityName       Name of the ability causing the attack (null for
     *                          base attack)
     * @param additionalTargets List of additional target piece IDs (for AoE attacks
     *                          like cleave)
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
            List<String> additionalTargets) {
        if (board == null || attacker == null || defender == null)
            return false;
        if (attacker == defender)
            return false;

        // Damage amount uses effective stats of the attacker
        int dmg = attacker.getEffectiveDamage();

        // Notify hooks first (mirrors existing patterns where appropriate)
        try {
            attacker.notifyAttack(defender, dmg);
        } catch (Exception ignored) {
        }
        try {
            defender.notifyDamaged(dmg, attacker);
        } catch (Exception ignored) {
        }

        // Deal damage
        defender.getStats().dealDamage(dmg);

        // Build targets list (primary target + additional targets)
        List<String> allTargets = new ArrayList<>();
        allTargets.add(defender.getId().toString());
        if (additionalTargets != null) {
            allTargets.addAll(additionalTargets);
        }

        // Emit attack event with ability reference and all targets
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("attackerId", attacker.getId().toString());
        eventData.put("defenderId", defender.getId().toString());
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
        // IMPORTANT: Get defender's current position (may have changed due to abilities
        // like PushOnAttackAbility)
        // Use the position from the defender's GamePieceData, not the original
        // parameters
        int actualDefenderRow = defenderRow;
        int actualDefenderCol = defenderCol;
        Object defenderPosObj = defender.getData(GamePieceData.POSITION);
        if (defenderPosObj instanceof Board.Position defenderPos) {
            actualDefenderRow = defenderPos.getRow();
            actualDefenderCol = defenderPos.getCol();
        }

        if (defender.getStats().getCurrentHealth() <= 0) {
            try {
                defender.notifyDied();
            } catch (Exception ignored) {
            }
            board.removeGamePieceAtPos(actualDefenderRow, actualDefenderCol);
            EventBus.emit(
                    GameEventType.PIECE_DIED,
                    Map.of(
                            "pieceId", defender.getId().toString(),
                            "row", actualDefenderRow,
                            "col", actualDefenderCol));
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
            int defenderCol) {
        return performAttack(
            board,
            attacker,
            defender,
            attackerRow,
            attackerCol,
            defenderRow,
            defenderCol,
            null,
            null
        );
    }

    private AbilityUtils() {
    }

    // --- Small API helpers for actionable abilities ---
    /** Returns the selection flow for the given actionable ability (null-safe). */
    public static ClickableEffectData selectionFor(ActionableAbility ability) {
        if (ability == null)
            return null;
        return ability.getClickableEffectData();
    }

    /**
     * Executes the actionable ability with the given entities map (null-safe).
     */
    public static void execute(ActionableAbility ability, HashMap<Integer, CustomBox> entities) {
        if (ability == null || entities == null)
            return;
        ability.execute(entities);
    }

    // --- Event emit helpers ---
    public static void emit(GameEventType type, Map<String, Object> data) {
        EventBus.emit(type, data);
    }

    public static void emit(GameEventType type, Object... kvPairs) {
        Map<String, Object> m = new HashMap<>();
        if (kvPairs != null) {
            for (int i = 0; i + 1 < kvPairs.length; i += 2) {
                Object k = kvPairs[i];
                Object v = kvPairs[i + 1];
                if (k != null)
                    m.put(String.valueOf(k), v);
            }
        }
        EventBus.emit(type, m);
    }

    /** Returns remaining actions for the given piece. */
    public static int getRemainingActions(MonsterGamePiece mgp) {
        return mgp.getStats().getRemainingActions();
    }

    /**
     * Check if a piece can act (has actions and is not stunned).
     * Stunned pieces cannot act even if they have remaining actions.
     */
    public static boolean canAct(MonsterGamePiece mgp) {
        if (mgp == null)
            return false;
        // Stunned pieces cannot act
        if (mgp.isStunned())
            return false;
        // Must have remaining actions
        return getRemainingActions(mgp) > 0;
    }

    /**
     * Spend 1 action from the given piece and emit ACTION_SPENT. Never goes below
     * zero.
     */
    public static void spendAction(MonsterGamePiece mgp) {
        int left = Math.max(0, getRemainingActions(mgp) - 1);
        mgp.getStats().setRemainingActions(left);
        EventBus.emit(
                GameEventType.ACTION_SPENT,
                Map.of(
                        "pieceId", mgp.getId().toString(),
                        "owner", mgp.getAlignment().name(),
                        "remaining", left));
    }

    /**
     * Deal damage to a target and emit PIECE_DIED if it dies. Returns true if
     * target remains alive.
     * (No generic PIECE_DAMAGED event exists in taxonomy yet.)
     */
    public static boolean dealDamage(MonsterGamePiece target, int amount, MonsterGamePiece source,
            boolean emitDeathEvent) {
        if (target == null || amount <= 0)
            return true;
        target.getStats().dealDamage(amount);
        if (target.getStats().isDead()) {
            target.die();
            if (emitDeathEvent) {
                emit(
                        GameEventType.PIECE_DIED,
                        "pieceId", target.getId().toString());
            }
            return false;
        }
        return true;
    }
}
