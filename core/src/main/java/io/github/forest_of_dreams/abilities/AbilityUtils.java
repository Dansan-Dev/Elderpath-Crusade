package io.github.forest_of_dreams.abilities;

import io.github.forest_of_dreams.data_objects.ClickableEffectData;
import io.github.forest_of_dreams.enums.GamePieceData;
import io.github.forest_of_dreams.game_objects.board.MonsterGamePiece;
import io.github.forest_of_dreams.interfaces.CustomBox;
import io.github.forest_of_dreams.multiplayer.EventBus;
import io.github.forest_of_dreams.multiplayer.GameEventType;

import java.util.HashMap;
import java.util.Map;

/**
 * Small static helpers for common ability-side operations.
 */
public final class AbilityUtils {
    private AbilityUtils() {}

    // --- Small API helpers for actionable abilities ---
    /** Returns the selection flow for the given actionable ability (null-safe). */
    public static ClickableEffectData selectionFor(ActionableAbility ability) {
        if (ability == null) return null;
        return ability.getClickableEffectData();
    }
    /** Executes the actionable ability with the given entities map (null-safe). */
    public static boolean execute(ActionableAbility ability, HashMap<Integer, CustomBox> entities) {
        if (ability == null || entities == null) return false;
        return ability.execute(entities);
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

    /** Returns remaining actions for the given piece (defaults to base actions if not set). */
    public static int getRemainingActions(MonsterGamePiece mgp) {
        Object v = mgp.getData(GamePieceData.ACTIONS_REMAINING);
        if (v instanceof Integer n) return n;
        return mgp.getStats().getActions();
    }

    /** Spend 1 action from the given piece and emit ACTION_SPENT. Never goes below zero. */
    public static void spendAction(MonsterGamePiece mgp) {
        int left = Math.max(0, getRemainingActions(mgp) - 1);
        mgp.updateData(GamePieceData.ACTIONS_REMAINING, left);
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
