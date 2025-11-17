package io.github.elderpath_crusade.abilities.impl;

import io.github.elderpath_crusade.abilities.ActionableAbility;
import io.github.elderpath_crusade.abilities.AbilityUtils;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.ClickableTargetType;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.managers.TurnManager;
import io.github.elderpath_crusade.multiplayer.EventBus;
import io.github.elderpath_crusade.multiplayer.GameEventType;
import io.github.elderpath_crusade.utils.Logger;
import lombok.AllArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * WarpMage actionable ability: "Displace"
 * Range 2 (square distance, includes diagonals): select another MonsterGamePiece,
 * then select an adjacent empty plot (cardinal) next to that target. The target is moved into that plot.
 * Costs 1 action from the WarpMage (owner) on success.
 */
@AllArgsConstructor
public class DisplaceAbility implements ActionableAbility {
    private final MonsterGamePiece owner;

    @Override
    public String getName() { return "Displace"; }

    @Override
    public String getDescription() {
        return DisplaceAbility.getAbilityDescription();
    }

    public static String getAbilityDescription() {
        return """
            Displace (1 action)
            Range 2 → move another target
            monster 1 step (cardinal) to
            an adjacent empty square""";
    }

    @Override
    public String getIconPath() {
        return "images/displace_ability.png";
    }

    @Override
    public ClickableEffectData getClickableEffectData() {
        // Require two picks; InteractionManager will record them as indices 1 and 2.
        // We set NONE to allow both GAME_PIECE and PLOT to be selected; execute() performs strict validation.
        return ClickableEffectData.getMulti(ClickableTargetType.NONE, 2);
    }

    private static Board.Position getPos(GamePiece gp) {
        Object posObj = gp.getData(GamePieceData.POSITION);
        if (posObj instanceof Board.Position pos) return pos;
        Logger.error("DisplaceAbility", "Missing POSITION on piece " + gp.getId());
        return null;
    }

    @Override
    public void execute(HashMap<Integer, CustomBox> entities) {
        // Basic turn and actions gating
        if (owner == null) return;
        if (TurnManager.getCurrentPlayer() != owner.getAlignment()) return;
        if (AbilityUtils.getRemainingActions(owner) <= 0) return;

        // Extract selections: index 1 = target piece, index 2 = destination plot
        CustomBox firstClicked = entities.get(1);
        CustomBox secondClicked = entities.get(2);
        if (!(firstClicked instanceof Plot sourcePlot)) return;
        if (!(secondClicked instanceof Plot destinationPlot)) return;

        // Resolve positions and board context
        Board.Position ownerPos = getPos(owner);
        if (ownerPos == null) return;
        Board board = ownerPos.getBoard();
        if (board == null) return;
        GamePiece sourcePiece = board.getGamePieceAtPlot(sourcePlot);
        if (!(sourcePiece instanceof MonsterGamePiece target)) return;
        if (target == owner) return; // must be "another" target
        int[] sourceIndices = board.getIndicesOfPlot(sourcePlot);
        if (sourceIndices == null) return;
        Board.Position sourcePos = new Board.Position(board, sourceIndices[0], sourceIndices[1]);
        int sourceRow = sourcePos.getRow();
        int sourceCol = sourcePos.getCol();

        int[] destinationIndices = board.getIndicesOfPlot(destinationPlot);
        if (destinationIndices == null) return;
        int destinationRow = destinationIndices[0];
        int destinationCol = destinationIndices[1];

        // Validate range: square (Chebyshev) distance ≤ 2 from owner to target
        int distanceMaxOfBothDimensions = Math.max(Math.abs(ownerPos.getRow() - sourceRow), Math.abs(ownerPos.getCol() - sourceCol));
        if (distanceMaxOfBothDimensions > 2) return;

        // Destination must be cardinally adjacent to target and empty
        boolean isAdjacent = (
            Math.abs(destinationRow - sourceRow) +
            Math.abs(destinationCol - sourceCol)
        ) == 1;
        if (!isAdjacent) return;

        // Destination must be empty
        if (board.getGamePieceAtPos(destinationRow, destinationCol) != null) return;

        // Perform move: move the TARGET into destination
        // Clear target's current cell and place into new cell
        board.moveGamePiece(
            sourceRow, sourceCol,
            destinationRow, destinationCol
        );
        target.updateData(
            GamePieceData.POSITION,
            new Board.Position(
                board,
                destinationRow, destinationCol
            )
        );
        // Mark cause as ABILITY-driven move
        target.updateData(GamePieceData.MOVE_CAUSE, "ABILITY");
        try {
            target.notifyMoved(
                sourceRow, sourceCol,
                destinationRow, destinationCol
            );
        } catch (Exception ignored) {}
        // Emit PIECE_MOVED for the target
        EventBus.emit(
                GameEventType.PIECE_MOVED,
                Map.of(
                        "pieceId", target.getId().toString(),
                        "owner", target.getAlignment().name(),
                        "fromRow", sourceRow,
                        "fromCol", sourceCol,
                        "toRow", destinationRow,
                        "toCol", destinationCol
                )
        );

        // Spend 1 action from owner
        AbilityUtils.spendAction(owner);
    }
}
