package io.github.elderpath_crusade.abilities.impl;

import io.github.elderpath_crusade.abilities.AbilityUtils;
import io.github.elderpath_crusade.abilities.BasicAbility;
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
import io.github.elderpath_crusade.managers.TurnManager;

import java.util.HashMap;
import java.util.List;

/**
 * Base movement ability that all pieces have by default.
 * Allows moving to reachable plots within speed range.
 * This is a BasicAbility - automatically available when piece is selected, not shown in ability popup.
 */
public class BaseMoveAbility implements BasicAbility {
    private final MonsterGamePiece owner;

    public BaseMoveAbility(MonsterGamePiece owner) {
        this.owner = owner;
    }

    @Override
    public String getName() { return "Move"; }

    @Override
    public String getDescription() { return "Move to an adjacent empty square"; }

    @Override
    public ClickableEffectData getClickableEffectData() {
        // Single target: empty plot
        return ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1);
    }

    @Override
    public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
        if (owner == null) return false;
        if (TurnManager.getCurrentPlayer() != owner.getAlignment()) return false;
        if (AbilityUtils.getRemainingActions(owner) <= 0) return false;

        // Resolve to Plot
        Plot plot = resolveToPlot(box);
        if (plot == null) return false;

        // Get owner's position
        Object ownerPosObj = owner.getData(GamePieceData.POSITION);
        if (!(ownerPosObj instanceof Board.Position ownerPos)) return false;
        Board board = ownerPos.getBoard();
        if (board == null) return false;

        // Get plot's position
        int[] plotIndices = board.getIndicesOfPlot(plot);
        if (plotIndices == null) return false;
        int plotRow = plotIndices[0];
        int plotCol = plotIndices[1];

        // Check if plot is reachable and empty
        int speed = owner.getEffectiveSpeed();
        List<Plot> reachable = board.getReachablePlots(ownerPos.getRow(), ownerPos.getCol(), speed);
        for (Plot p : reachable) {
            if (p == plot) {
                // Also check it's empty
                return !board.isOccupied(plotRow, plotCol);
            }
        }
        return false;
    }

    @Override
    public List<Plot> getEligibleTargets(int targetIndex) {
        if (owner == null) return List.of();
        if (TurnManager.getCurrentPlayer() != owner.getAlignment()) return List.of();
        if (AbilityUtils.getRemainingActions(owner) <= 0) return List.of();

        Object ownerPosObj = owner.getData(GamePieceData.POSITION);
        if (!(ownerPosObj instanceof Board.Position ownerPos)) return List.of();
        Board board = ownerPos.getBoard();
        if (board == null) return List.of();

        int speed = owner.getEffectiveSpeed();
        List<Plot> reachable = board.getReachablePlots(
            ownerPos.getRow(),
            ownerPos.getCol(),
            speed
        );
        // Filter to only empty plots
        return reachable.stream()
                .filter(plot -> {
                    int[] indices = board.getIndicesOfPlot(plot);
                    if (indices == null) return false;
                    return !board.isOccupied(indices[0], indices[1]);
                })
                .toList();
    }

    @Override
    public void execute(HashMap<Integer, CustomBox> entities) {
        if (owner == null) return;
        if (TurnManager.getCurrentPlayer() != owner.getAlignment()) return;
        if (AbilityUtils.getRemainingActions(owner) <= 0) return;

        // Get destination plot
        CustomBox firstClicked = entities.get(1);
        Plot destinationPlot = resolveToPlot(firstClicked);
        if (destinationPlot == null) return;

        // Get owner's position and board
        Object ownerPosObj = owner.getData(GamePieceData.POSITION);
        if (!(ownerPosObj instanceof Board.Position ownerPos)) return;
        Board board = ownerPos.getBoard();
        if (board == null) return;

        int ownerRow = ownerPos.getRow();
        int ownerCol = ownerPos.getCol();

        // Get destination plot's position
        int[] destIndices = board.getIndicesOfPlot(destinationPlot);
        if (destIndices == null) return;
        int destRow = destIndices[0];
        int destCol = destIndices[1];

        // Validate that destination is reachable and empty
        int speed = owner.getEffectiveSpeed();
        List<Plot> reachable = board.getReachablePlots(ownerRow, ownerCol, speed);
        boolean valid = false;
        for (Plot p : reachable) {
            if (p == destinationPlot) {
                valid = true;
                break;
            }
        }
        if (!valid) return;
        if (board.isOccupied(destRow, destCol)) return;

        // Perform the movement
        MovementUtils.performActiveMovement(
                board,
                owner,
                ownerRow,
                ownerCol,
                destRow,
                destCol
        );
    }

    private Plot resolveToPlot(CustomBox box) {
        if (box instanceof Plot plot) return plot;
        if (box instanceof GamePiece gp) {
            Object posObj = gp.getData(GamePieceData.POSITION);
            if (posObj instanceof Board.Position pos) {
                Board board = pos.getBoard();
                if (board != null) {
                    Renderable renderable = board.getPlotAtPos(pos.getRow(), pos.getCol());
                    if (renderable instanceof Plot p) return p;
                }
            }
        }
        return null;
    }
}

