package io.github.elderpath_crusade.abilities;

import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.multiplayer.EventBus;
import io.github.elderpath_crusade.multiplayer.GameEventType;

import java.util.HashMap;
import java.util.Map;

/**
 * Small static helpers for common ability-side operations.
 * Returns boolean value for success/failure.
 */
public final class AbilityUtils {
    // Centralized basic attack resolution for abilities and interactions.
    // Performs damage, notifies hooks, emits PIECE_ATTACKED, and removes/announces death with coordinates.
    public static boolean performBasicAttack(
            Board board,
            MonsterGamePiece attacker,
            MonsterGamePiece defender,
            int attackerRow,
            int attackerCol,
            int targetRow,
            int targetCol
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
        // Emit attack event with full coordinates
        EventBus.emit(
                GameEventType.PIECE_ATTACKED,
                Map.of(
                        "attackerId", attacker.getId().toString(),
                        "defenderId", defender.getId().toString(),
                        "row", targetRow,
                        "col", targetCol,
                        "attackerRow", attackerRow,
                        "attackerCol", attackerCol,
                        "defenderRow", targetRow,
                        "defenderCol", targetCol,
                        "damage", dmg
                )
        );
        // Handle death
        if (defender.getStats().getCurrentHealth() <= 0) {
            try { defender.notifyDied(); } catch (Exception ignored) {}
            board.removeGamePieceAtPos(targetRow, targetCol);
            EventBus.emit(
                    GameEventType.PIECE_DIED,
                    Map.of(
                            "pieceId", defender.getId().toString(),
                            "row", targetRow,
                            "col", targetCol
                    )
            );
        }
        return true;
    }
    private AbilityUtils() {}

    // --- Small API helpers for actionable abilities ---
    /** Returns the selection flow for the given actionable ability (null-safe). */
    public static ClickableEffectData selectionFor(ActionableAbility ability) {
        if (ability == null) return null;
        return ability.getClickableEffectData();
    }
    /**
     * Executes the actionable ability with the given entities map (null-safe).
     */
    public static void execute(ActionableAbility ability, HashMap<Integer, CustomBox> entities) {
        if (ability == null || entities == null) return;
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
                if (k != null) m.put(String.valueOf(k), v);
            }
        }
        EventBus.emit(type, m);
    }

    /** Returns remaining actions for the given piece. */
    public static int getRemainingActions(MonsterGamePiece mgp) {
        return mgp.getStats().getRemainingActions();
    }

    /** Spend 1 action from the given piece and emit ACTION_SPENT. Never goes below zero. */
    public static void spendAction(MonsterGamePiece mgp) {
        int left = Math.max(0, getRemainingActions(mgp) - 1);
        mgp.getStats().setRemainingActions(left);
        EventBus.emit(
            GameEventType.ACTION_SPENT,
            Map.of(
                "pieceId", mgp.getId().toString(),
                "owner", mgp.getAlignment().name(),
                "remaining", left
            )
        );
    }

    /**
     * Deal damage to a target and emit PIECE_DIED if it dies. Returns true if target remains alive.
     * (No generic PIECE_DAMAGED event exists in taxonomy yet.)
     */
    public static boolean dealDamage(MonsterGamePiece target, int amount, MonsterGamePiece source, boolean emitDeathEvent) {
        if (target == null || amount <= 0) return true;
        target.getStats().dealDamage(amount);
        if (target.getStats().isDead()) {
            target.die();
            if (emitDeathEvent) {
                emit(
                    GameEventType.PIECE_DIED,
                    "pieceId", target.getId().toString()
                );
            }
            return false;
        }
        return true;
    }
}
