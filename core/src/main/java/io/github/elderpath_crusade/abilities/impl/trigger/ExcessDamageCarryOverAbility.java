package io.github.elderpath_crusade.abilities.impl.trigger;

import io.github.elderpath_crusade.abilities.AbilityType;
import io.github.elderpath_crusade.utils.AbilityUtils;
import io.github.elderpath_crusade.abilities.TriggeredAbility;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.game_piece.GamePiece;
import io.github.elderpath_crusade.game_objects.board.game_piece.MonsterGamePiece;
import io.github.elderpath_crusade.multiplayer.EventBus;
import io.github.elderpath_crusade.multiplayer.GameEvent;
import io.github.elderpath_crusade.multiplayer.GameEventType;

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
    private Consumer<GameEvent> diedListener;

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

        // Track this attack
        trackedTarget = target;
        trackedDamage = damage;
        targetHealthBeforeAttack = target.getStats().getCurrentHealth();

        // Get positions
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

        GameEventType t = event.getType();
        if (t == GameEventType.PIECE_DIED) {
            Object pieceIdObj = event.getData().get("pieceId");
            if (pieceIdObj != null && pieceIdObj.toString().equals(trackedTarget.getId().toString())) {
                // Target died - check for excess damage
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

        // Calculate excess damage (damage dealt - health target had before)
        int excessDamage = trackedDamage - targetHealthBeforeAttack;
        if (excessDamage <= 0) {
            // No excess damage
            return;
        }

        // Get board
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

        // Calculate direction from attacker to target
        int rowDir = Integer.compare(targetRow, attackerRow);
        int colDir = Integer.compare(targetCol, attackerCol);

        // Must be cardinal direction (one of rowDir or colDir must be 0)
        if (rowDir != 0 && colDir != 0) {
            // Diagonal - no carry over
            return;
        }

        // Find closest enemy behind target (continuing in same direction)
        int attackerRange = owner.getEffectiveRange();
        MonsterGamePiece closestEnemy = null;
        int closestDistance = Integer.MAX_VALUE;

        // Continue in the same direction from target
        for (int dist = 1; dist <= attackerRange; dist++) {
            int checkRow = targetRow + rowDir * dist;
            int checkCol = targetCol + colDir * dist;

            // Check bounds
            if (checkRow < 0 || checkRow >= board.getROWS() || checkCol < 0 || checkCol >= board.getCOLS()) {
                break; // Out of bounds
            }

            // Check if blocked by terrain or friendly unit
            GamePiece piece = board.getGamePieceAtPos(checkRow, checkCol);
            if (piece != null) {
                if (piece.getType() == io.github.elderpath_crusade.enums.settings.GamePieceType.TERRAIN) {
                    break; // Blocked by terrain
                }
                if (piece instanceof MonsterGamePiece mgp) {
                    if (mgp.getAlignment() == owner.getAlignment()) {
                        break; // Blocked by friendly unit
                    }
                    // Found enemy - check if it's closer than previous
                    if (dist < closestDistance) {
                        closestEnemy = mgp;
                        closestDistance = dist;
                        // Don't break - continue to find the closest one
                    }
                }
            }
        }

        // Deal excess damage to closest enemy if found
        if (closestEnemy != null && closestDistance < Integer.MAX_VALUE) {
            AbilityUtils.dealDamage(closestEnemy, excessDamage, owner, true);
            try {
                closestEnemy.notifyDamaged(excessDamage, owner);
            } catch (Exception ignored) {}
        }
    }

    private void registerDiedListener() {
        diedListener = this::onGameEvent;
        EventBus.register(GameEventType.PIECE_DIED, diedListener);
    }

    private void unregisterDiedListener() {
        if (diedListener != null) {
            EventBus.unregister(GameEventType.PIECE_DIED, diedListener);
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

