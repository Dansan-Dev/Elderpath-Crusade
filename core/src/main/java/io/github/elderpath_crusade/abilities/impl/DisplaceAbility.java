package io.github.elderpath_crusade.abilities.impl;

import io.github.elderpath_crusade.abilities.ActionableAbility;
import io.github.elderpath_crusade.abilities.AbilityUtils;
import io.github.elderpath_crusade.abilities.MovementUtils;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.ClickableTargetType;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.interfaces.TargetFilter;
import io.github.elderpath_crusade.managers.InteractionManager;
import io.github.elderpath_crusade.managers.TurnManager;
import io.github.elderpath_crusade.multiplayer.EventBus;
import io.github.elderpath_crusade.multiplayer.GameEventType;
import io.github.elderpath_crusade.utils.Logger;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WarpMage actionable ability: "Displace"
 * Range 2 (square distance, includes diagonals): select another MonsterGamePiece,
 * then select an adjacent empty plot (cardinal) next to that target. The target is moved into that plot.
 * Costs 1 action from the WarpMage (owner) on success.
 */
@AllArgsConstructor
public class DisplaceAbility implements ActionableAbility, TargetFilter {
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
        if (!AbilityUtils.canAct(owner)) return;

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

        // Perform forced movement using MovementUtils
        MovementUtils.performForcedMovement(
            board,
            target,
            sourceRow,
            sourceCol,
            destinationRow,
            destinationCol,
            "ABILITY",
            "Displace"
        );

        // Spend 1 action from owner
        AbilityUtils.spendAction(owner);
    }

    // TargetFilter implementation
    @Override
    public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
        if (owner == null || box == null) return false;
        Board.Position ownerPos = getPos(owner);
        if (ownerPos == null) return false;
        Board board = ownerPos.getBoard();
        if (board == null) return false;

        // Resolve box to a Plot
        Plot targetPlot = resolveToPlot(board, box);
        if (targetPlot == null) return false;

        if (targetIndex == 1) {
            // First target: must be a plot containing a MonsterGamePiece within range 2, and not the owner
            GamePiece piece = board.getGamePieceAtPlot(targetPlot);
            if (!(piece instanceof MonsterGamePiece target)) return false;
            if (target == owner) return false; // must be "another" target

            int[] targetIndices = board.getIndicesOfPlot(targetPlot);
            if (targetIndices == null) return false;
            int targetRow = targetIndices[0];
            int targetCol = targetIndices[1];

            // Validate range: square (Chebyshev) distance ≤ 2 from owner to target
            int distanceMaxOfBothDimensions = Math.max(
                Math.abs(ownerPos.getRow() - targetRow),
                Math.abs(ownerPos.getCol() - targetCol)
            );
            return distanceMaxOfBothDimensions <= 2;
        } else if (targetIndex == 2) {
            // Second target: must be a plot that is cardinally adjacent to the first target and empty
            List<CustomBox> activeTargets = InteractionManager.getActiveTargets();
            if (activeTargets == null || activeTargets.isEmpty()) return false;
            CustomBox firstTarget = activeTargets.get(0);
            Plot firstPlot = resolveToPlot(board, firstTarget);
            if (firstPlot == null) return false;

            int[] firstIndices = board.getIndicesOfPlot(firstPlot);
            if (firstIndices == null) return false;
            int[] secondIndices = board.getIndicesOfPlot(targetPlot);
            if (secondIndices == null) return false;

            // Must be cardinally adjacent
            boolean isAdjacent = (
                Math.abs(secondIndices[0] - firstIndices[0]) +
                Math.abs(secondIndices[1] - firstIndices[1])
            ) == 1;
            if (!isAdjacent) return false;

            // Must be empty
            return board.getGamePieceAtPos(secondIndices[0], secondIndices[1]) == null;
        }

        return false;
    }

    @Override
    public List<Plot> getEligibleTargets(int targetIndex) {
        if (owner == null) return null;
        Board.Position ownerPos = getPos(owner);
        if (ownerPos == null) return null;
        Board board = ownerPos.getBoard();
        if (board == null) return null;

        List<Plot> eligible = new ArrayList<>();

        if (targetIndex == 1) {
            // Return all plots containing eligible MonsterGamePieces (within range 2, not owner)
            int ownerRow = ownerPos.getRow();
            int ownerCol = ownerPos.getCol();
            int rows = board.getROWS();
            int cols = board.getCOLS();

            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    // Check Chebyshev distance
                    int distanceMax = Math.max(
                        Math.abs(row - ownerRow),
                        Math.abs(col - ownerCol)
                    );
                    if (distanceMax > 2) continue;

                    GamePiece piece = board.getGamePieceAtPos(row, col);
                    if (!(piece instanceof MonsterGamePiece target)) continue;
                    if (target == owner) continue; // must be "another" target

                    Renderable renderable = board.getPlotAtPos(row, col);
                    if (renderable instanceof Plot plot) {
                        eligible.add(plot);
                    }
                }
            }
        } else if (targetIndex == 2) {
            // Return all plots that are cardinally adjacent to the first selected target and empty
            List<CustomBox> activeTargets = InteractionManager.getActiveTargets();
            if (activeTargets == null || activeTargets.isEmpty()) return null;
            CustomBox firstTarget = activeTargets.get(0);
            Plot firstPlot = resolveToPlot(board, firstTarget);
            if (firstPlot == null) return null;

            int[] firstIndices = board.getIndicesOfPlot(firstPlot);
            if (firstIndices == null) return null;
            int firstRow = firstIndices[0];
            int firstCol = firstIndices[1];

            // Check all 4 cardinal directions
            int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            for (int[] dir : directions) {
                int newRow = firstRow + dir[0];
                int newCol = firstCol + dir[1];
                if (newRow < 0 || newRow >= board.getROWS() || newCol < 0 || newCol >= board.getCOLS()) continue;
                if (board.getGamePieceAtPos(newRow, newCol) != null) continue; // must be empty

                Renderable renderable = board.getPlotAtPos(newRow, newCol);
                if (renderable instanceof Plot plot) {
                    eligible.add(plot);
                }
            }
        }

        return eligible.isEmpty() ? null : eligible;
    }

    // Helper to resolve either a Plot or a GamePiece into a Plot on the given board
    private static Plot resolveToPlot(Board board, CustomBox box) {
        if (box instanceof Plot p) return p;
        if (box instanceof GamePiece gp) {
            Object posObj = gp.getData(GamePieceData.POSITION);
            if (posObj instanceof Board.Position pos && pos.getBoard() == board) {
                var r = pos.getRow();
                var c = pos.getCol();
                var rp = board.getPlotAtPos(r, c);
                if (rp instanceof Plot pp) return pp;
            }
        }
        return null;
    }
}
