package io.github.elderpath_crusade.abilities.impl.trigger;

import io.github.elderpath_crusade.abilities.AbilityType;
import io.github.elderpath_crusade.utils.AbilityUtils;
import io.github.elderpath_crusade.abilities.TriggeredAbility;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.events.GameEvent;
import io.github.elderpath_crusade.events.PieceDiedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;

import java.util.function.Consumer;

/**
 * Crossbowman ability: Excess damage from attacks carries over to the closest enemy unit behind the target in range.
 * When a target dies from the attack, any excess damage is dealt to the closest enemy continuing in the same cardinal direction.
 */
public class ExcessDamageCarryOverAbility implements TriggeredAbility {
    private MonsterGamePiece owner;
    private MonsterGamePiece trackedTarget = null;
    private int trackedDamage = 0;
    private int targetHealthBeforeAttack = 0;
    private int attackerRow = -1;
    private int attackerCol = -1;
    private int targetRow = -1;
    private int targetCol = -1;
    private Consumer<PieceDiedEvent> diedListener;

    @Override
    public String getName() { return "Excess Damage"; }

    @Override
    public String getDescription() { return getAbilityDescription(); }

    public static String getAbilityDescription() {
        return "Excess damage carries over to closest enemy behind target";
    }

    @Override
    public AbilityType getType() { return AbilityType.TRIGGERED; }

    @Override
    public void onAttach(MonsterGamePiece owner) {
        this.owner = owner;
        registerDiedListener();
    }

    @Override
    public void onDetach() {
        unregisterDiedListener();
        clearTracking();
        this.owner = null;
    }

    @Override
    public void onOwnerAttack(MonsterGamePiece owner, MonsterGamePiece target, int damage) {
        if (this.owner == null || owner != this.owner) return;
        if (target == null) return;

        trackedTarget = target;
        trackedDamage = damage;
        targetHealthBeforeAttack = target.getStats().getCurrentHealth();

        Object ownerPosObj = owner.getData(GamePieceData.POSITION);
        Object targetPosObj = target.getData(GamePieceData.POSITION);
        if (ownerPosObj instanceof Board.Position ownerPos && targetPosObj instanceof Board.Position targetPos) {
            attackerRow = ownerPos.getRow();
            attackerCol = ownerPos.getCol();
            targetRow = targetPos.getRow();
            targetCol = targetPos.getCol();
        }
    }

    @Override
    public void onGameEvent(GameEvent event) {
        if (owner == null) return;
        if (trackedTarget == null) return;

        if (event instanceof PieceDiedEvent died) {
            if (died.pieceId().equals(trackedTarget.getId().toString())) {
                handleExcessDamage();
                clearTracking();
            }
        }
    }

    private void handleExcessDamage() {
        if (trackedTarget == null) return;
        if (attackerRow < 0 || attackerCol < 0 || targetRow < 0 || targetCol < 0) {
            clearTracking();
            return;
        }

        int excessDamage = trackedDamage - targetHealthBeforeAttack;
        if (excessDamage <= 0) return;

        Object ownerPosObj = owner.getData(GamePieceData.POSITION);
        if (!(ownerPosObj instanceof Board.Position ownerPos)) {
            clearTracking();
            return;
        }
        Board board = ownerPos.getBoard();
        if (board == null) {
            clearTracking();
            return;
        }

        int rowDir = Integer.compare(targetRow, attackerRow);
        int colDir = Integer.compare(targetCol, attackerCol);

        if (rowDir != 0 && colDir != 0) return;

        int attackerRange = owner.getEffectiveRange();
        MonsterGamePiece closestEnemy = null;
        int closestDistance = Integer.MAX_VALUE;

        for (int dist = 1; dist <= attackerRange; dist++) {
            int checkRow = targetRow + rowDir * dist;
            int checkCol = targetCol + colDir * dist;

            if (checkRow < 0 || checkRow >= board.getROWS() || checkCol < 0 || checkCol >= board.getCOLS()) break;

            GamePiece piece = board.getGamePieceAtPos(checkRow, checkCol);
            if (piece != null) {
                if (piece.getType() == io.github.elderpath_crusade.enums.settings.GamePieceType.TERRAIN) break;
                if (piece instanceof MonsterGamePiece mgp) {
                    if (mgp.getAlignment() == owner.getAlignment()) break;
                    if (dist < closestDistance) {
                        closestEnemy = mgp;
                        closestDistance = dist;
                    }
                }
            }
        }

        if (closestEnemy != null) {
            AbilityUtils.dealDamage(closestEnemy, excessDamage, owner, true);
            try {
                closestEnemy.notifyDamaged(excessDamage, owner);
            } catch (Exception ignored) {}
        }
    }

    private void registerDiedListener() {
        diedListener = e -> onGameEvent(e);
        TypedEventBus.get().register(PieceDiedEvent.class, diedListener);
    }

    private void unregisterDiedListener() {
        if (diedListener != null) {
            TypedEventBus.get().unregister(PieceDiedEvent.class, diedListener);
            diedListener = null;
        }
    }

    private void clearTracking() {
        trackedTarget = null;
        trackedDamage = 0;
        targetHealthBeforeAttack = 0;
        attackerRow = -1;
        attackerCol = -1;
        targetRow = -1;
        targetCol = -1;
    }
}
