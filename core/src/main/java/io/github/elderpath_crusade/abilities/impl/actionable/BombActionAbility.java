package io.github.elderpath_crusade.abilities.impl.actionable;

import io.github.elderpath_crusade.abilities.AbilityType;
import io.github.elderpath_crusade.utils.AbilityUtils;
import io.github.elderpath_crusade.abilities.ActionableAbility;
import io.github.elderpath_crusade.abilities.TriggeredAbility;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.managers.TurnManager;

import java.util.HashMap;

/**
 * SkeletonBomber actionable ability: "Bomb"
 * Immediate effect: Deal damage equal to owner's attack to all units within 1 square (centered on SkeletonBomber).
 * Costs 1 action and can only be used once per turn.
 */
public class BombActionAbility implements ActionableAbility, TriggeredAbility {
    private final MonsterGamePiece owner;
    private boolean usedThisTurn = false;

    public BombActionAbility(MonsterGamePiece owner) {
        this.owner = owner;
    }

    @Override
    public String getName() { return "Bomb"; }

    @Override
    public String getDescription() { return BombActionAbility.getAbilityDescription(); }

    public static String getAbilityDescription() {
        return "BOMB ACTION (1/turn): Deal damage equal to attack to all units within 1 square";
    }

    @Override
    public AbilityType getType() { return AbilityType.ACTIONABLE; }

    @Override
    public String getIconPath() { return null; }

    @Override
    public ClickableEffectData getClickableEffectData() {
        // Immediate effect - no target selection needed
        return ClickableEffectData.getImmediate();
    }

    @Override
    public void execute(HashMap<Integer, CustomBox> entities) {
        if (owner == null) return;
        if (usedThisTurn) return;
        if (TurnManager.getCurrentPlayer() != owner.getAlignment()) return;
        if (!AbilityUtils.canAct(owner)) return;

        // Get owner's position and board (bomb always centered on owner)
        Object ownerPosObj = owner.getData(GamePieceData.POSITION);
        if (!(ownerPosObj instanceof Board.Position ownerPos)) return;
        Board board = ownerPos.getBoard();
        if (board == null) return;

        int centerRow = ownerPos.getRow();
        int centerCol = ownerPos.getCol();

        // Get owner's effective damage
        int damage = owner.getEffectiveDamage();

        // Deal damage to all units within Chebyshev distance 1 from owner (9 squares: center + 8 surrounding)
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int r = centerRow + dr;
                int c = centerCol + dc;
                if (r < 0 || r >= board.getROWS() || c < 0 || c >= board.getCOLS()) continue;

                GamePiece piece = board.getGamePieceAtPos(r, c);
                if (piece instanceof MonsterGamePiece target) {
                    // Deal damage to all units (both friendly and enemy)
                    AbilityUtils.dealDamage(target, damage, owner, true);
                    try {
                        target.notifyDamaged(damage, owner);
                    } catch (Exception ignored) {}
                }
            }
        }

        // Mark as used this turn
        usedThisTurn = true;

        // Spend 1 action from owner
        AbilityUtils.spendAction(owner);
    }

    @Override
    public void onAttach(MonsterGamePiece owner) {
        // No-op, owner is set in constructor
    }

    @Override
    public void onDetach() {
        // No-op
    }

    @Override
    public void onTurnEnded(PieceAlignment endingPlayer) {
        // Reset usage when owner's turn ends
        if (owner != null && endingPlayer == owner.getAlignment()) {
            usedThisTurn = false;
        }
    }

}

