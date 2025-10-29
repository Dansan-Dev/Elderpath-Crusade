package io.github.forest_of_dreams.abilities.impl;

import io.github.forest_of_dreams.abilities.AbilityType;
import io.github.forest_of_dreams.abilities.ActionableAbility;
import io.github.forest_of_dreams.abilities.AbilityUtils;
import io.github.forest_of_dreams.data_objects.ClickableEffectData;
import io.github.forest_of_dreams.enums.ClickableTargetType;
import io.github.forest_of_dreams.enums.GamePieceData;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.game_objects.board.Board;
import io.github.forest_of_dreams.game_objects.board.GamePiece;
import io.github.forest_of_dreams.game_objects.board.MonsterGamePiece;
import io.github.forest_of_dreams.game_objects.board.Plot;
import io.github.forest_of_dreams.interfaces.CustomBox;
import io.github.forest_of_dreams.managers.TurnManager;
import io.github.forest_of_dreams.multiplayer.EventBus;
import io.github.forest_of_dreams.multiplayer.GameEventType;
import io.github.forest_of_dreams.utils.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * WarpMage actionable ability: "Displace"
 * Range 2 (square distance, includes diagonals): select another MonsterGamePiece,
 * then select an adjacent empty plot (cardinal) next to that target. The target is moved into that plot.
 * Costs 1 action from the WarpMage (owner) on success.
 */
public class DisplaceAbility implements ActionableAbility {
    private final MonsterGamePiece owner;

    public DisplaceAbility(MonsterGamePiece owner) {
        this.owner = owner;
    }

    @Override
    public String getName() { return "Displace"; }

    @Override
    public String getDescription() {
        return DisplaceAbility.getAbilityDescription();
    }

    public static String getAbilityDescription() {
        return "Displace (1 action)\n" +
            "Range 2 → move another target\n" +
            "monster 1 step (cardinal) to\n" +
            "an adjacent empty square";
    }

    @Override
    public String getIconPath() {
        // LibGDX internal path relative to assets/
        return "images/displace_ability.png";
    }

    @Override
    public ClickableEffectData getClickableEffectData() {
        // Require two picks; InteractionManager will record them as indices 1 and 2.
        // We set NONE to allow both GAME_PIECE and PLOT to be selected; execute() performs strict validation.
        return ClickableEffectData.getMulti(ClickableTargetType.NONE, 2);
    }

    @Override
    public boolean execute(HashMap<Integer, CustomBox> entities) {
        // Basic turn and actions gating
        if (owner == null) return false;
        if (TurnManager.getCurrentPlayer() != owner.getAlignment()) return false;
        if (AbilityUtils.getRemainingActions(owner) <= 0) return false;

        // Extract selections: index 1 = target piece, index 2 = destination plot
        Object t = entities.get(1);
        Object p = entities.get(2);
        if (!(t instanceof Plot src)) return false;
        if (!(p instanceof Plot dst)) return false;

        // Resolve positions and board context
        Board.Position ownerPos = getPos(owner);
        if (ownerPos == null) return false;
        Board board = ownerPos.getBoard();
        if (board == null) return false;
        GamePiece srcPiece = board.getGamePieceAtPlot(src);
        if (!(srcPiece instanceof MonsterGamePiece target)) return false;
        if (target == owner) return false; // must be "another" target
        int[] targetIndices = board.getIndicesOfPlot(src);
        if (targetIndices == null) return false;
        Board.Position targetPos = new Board.Position(board, targetIndices[0], targetIndices[1]);


        int tr = targetPos.getRow();
        int tc = targetPos.getCol();
        int[] dIdx = board.getIndicesOfPlot(dst);
        if (dIdx == null) return false;
        int dr = dIdx[0], dc = dIdx[1];

        // Validate range: square (Chebyshev) distance ≤ 2 from owner to target
        int chebyshev = Math.max(Math.abs(ownerPos.getRow() - tr), Math.abs(ownerPos.getCol() - tc));
        if (chebyshev > 2) return false;

        // Destination must be cardinally adjacent to target and empty
        int manhattan = Math.abs(dr - tr) + Math.abs(dc - tc);
        if (manhattan != 1) return false;
        if (board.getGamePieceAtPos(dr, dc) != null) return false;

        // Perform move: move the TARGET into destination
        // Clear target's current cell and place into new cell
        board.moveGamePiece(tr, tc, dr, dc);
        target.updateData(GamePieceData.POSITION, new Board.Position(board, dr, dc));
        try { target.notifyMoved(tr, tc, dr, dc); } catch (Exception ignored) {}
        // Emit PIECE_MOVED for the target
        EventBus.emit(
                GameEventType.PIECE_MOVED,
                Map.of(
                        "pieceId", target.getId().toString(),
                        "owner", target.getAlignment().name(),
                        "fromRow", tr,
                        "fromCol", tc,
                        "toRow", dr,
                        "toCol", dc
                )
        );

        // Spend 1 action from owner
        AbilityUtils.spendAction(owner);
        return true;
    }

    private static Board.Position getPos(GamePiece gp) {
        Object posObj = gp.getData(GamePieceData.POSITION);
        if (posObj instanceof Board.Position pos) return pos;
        Logger.error("DisplaceAbility", "Missing POSITION on piece " + gp.getId());
        return null;
    }
}
