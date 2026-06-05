package io.github.elderpath_crusade.utils;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.events.*;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;

import java.util.List;

/**
 * Static helpers for common game operations (damage, actions, events).
 */
public final class AbilityUtils {
    private AbilityUtils() {}

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
        if (board == null || attacker == null || defender == null) return false;
        if (attacker == defender) return false;

        int dmg = attacker.getEffectiveDamage();

        if (defender.getEntity() != null) {
            GameContext.get().getCombatSystem().applyDamage(defender.getEntity(), dmg);
        } else {
            defender.getStats().dealDamage(dmg);
        }

        TypedEventBus.get().emit(new PieceAttackedEvent(
                attacker.getId().toString(),
                attacker.getAlignment(),
                attackerRow, attackerCol,
                defender.getId().toString(),
                defenderRow, defenderCol,
                dmg,
                additionalTargets,
                abilityName));

        int actualDefenderRow = defenderRow;
        int actualDefenderCol = defenderCol;
        Object defenderPosObj = defender.getData(GamePieceData.POSITION);
        if (defenderPosObj instanceof Board.Position defenderPos) {
            actualDefenderRow = defenderPos.getRow();
            actualDefenderCol = defenderPos.getCol();
        }

        if (defender.getStats().getCurrentHealth() <= 0) {
            board.removeGamePieceAtPos(actualDefenderRow, actualDefenderCol);
            TypedEventBus.get().emit(new PieceDiedEvent(
                    defender.getId().toString(), actualDefenderRow, actualDefenderCol));
        }

        return true;
    }

    public static boolean performAttack(
            Board board,
            MonsterGamePiece attacker,
            MonsterGamePiece defender,
            int attackerRow,
            int attackerCol,
            int defenderRow,
            int defenderCol) {
        return performAttack(board, attacker, defender, attackerRow, attackerCol, defenderRow, defenderCol, null, null);
    }

    public static void emit(GameEvent event) {
        TypedEventBus.get().emit(event);
    }

    public static int getRemainingActions(MonsterGamePiece mgp) {
        return mgp.getStats().getRemainingActions();
    }

    public static boolean canAct(MonsterGamePiece mgp) {
        if (mgp == null) return false;
        if (mgp.isStunned()) return false;
        return getRemainingActions(mgp) > 0;
    }

    public static void spendAction(MonsterGamePiece mgp) {
        int left = Math.max(0, getRemainingActions(mgp) - 1);
        mgp.getStats().setRemainingActions(left);
        TypedEventBus.get().emit(new ActionSpentEvent(
                mgp.getId().toString(), mgp.getAlignment(), left));
    }

    /**
     * Deal damage to a target. Returns true if target remains alive.
     */
    public static boolean dealDamage(MonsterGamePiece target, int amount, MonsterGamePiece source,
            boolean emitDeathEvent) {
        if (target == null || amount <= 0) return true;

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
