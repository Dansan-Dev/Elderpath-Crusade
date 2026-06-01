package io.github.elderpath_crusade.utils;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.abilities.ActionableAbility;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.events.*;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.interfaces.CustomBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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

        // Deal damage via ECS CombatSystem when entity available
        if (defender.getEntity() != null) {
            GameContext.get().getCombatSystem().applyDamage(defender.getEntity(), dmg);
        } else {
            defender.getStats().dealDamage(dmg);
        }

        // Emit attack event
        TypedEventBus.get().emit(new PieceAttackedEvent(
                attacker.getId().toString(),
                attacker.getAlignment(),
                attackerRow, attackerCol,
                defender.getId().toString(),
                defenderRow, defenderCol,
                dmg,
                additionalTargets,
                abilityName
        ));

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
            TypedEventBus.get().emit(new PieceDiedEvent(
                    defender.getId().toString(), actualDefenderRow, actualDefenderCol));
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
    public static void emit(GameEvent event) {
        TypedEventBus.get().emit(event);
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
        TypedEventBus.get().emit(new ActionSpentEvent(
                mgp.getId().toString(), mgp.getAlignment(), left));
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

        // Route through ECS CombatSystem when entity is available
        boolean died;
        if (target.getEntity() != null) {
            died = GameContext.get().getCombatSystem().applyDamage(target.getEntity(), amount);
        } else {
            target.getStats().dealDamage(amount);
            died = target.getStats().isDead();
        }

        if (died) {
            target.die();
            if (emitDeathEvent) {
                TypedEventBus.get().emit(new PieceDiedEvent(
                        target.getId().toString(), -1, -1));
            }
            return false;
        }
        return true;
    }
}
