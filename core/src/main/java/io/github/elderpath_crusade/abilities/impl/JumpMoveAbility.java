package io.github.elderpath_crusade.abilities.impl;

import io.github.elderpath_crusade.abilities.AbilityUtils;
import io.github.elderpath_crusade.abilities.BasicAbility;
import io.github.elderpath_crusade.abilities.MovementUtils;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.ClickableTargetType;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.managers.TurnManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Jump movement ability for BigToad.
 * Allows moving in cardinal directions only, jumping over terrain and units.
 * Movement distance is limited by speed.
 * This is a BasicAbility - automatically available when piece is selected, not shown in ability popup.
 */
public class JumpMoveAbility implements BasicAbility {
    private final MonsterGamePiece owner;

    public JumpMoveAbility(MonsterGamePiece owner) {
        this.owner = owner;
    }

    @Override
    public String getName() { return "Jump Move"; }

    @Override
    public String getDescription() { return "Jump to a plot in cardinal direction, ignoring terrain and units"; }

    @Override
    public ClickableEffectData getClickableEffectData() {
        // Single target: empty plot
        return ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1);
    }

    /**
     * Calculate reachable plots with jump movement logic:
     * - Cardinal directions only (N/E/S/W)
     * - Can pass through terrain and units (jump over)
     * - Cannot end on occupied cells
     * - Distance limited by speed
     */
    private List<Plot> getJumpReachablePlots(Board board, int row, int col, int speed) {
        List<Plot> out = new ArrayList<>();
        if (speed <= 0 || board == null) return out;

        int rows = board.getROWS();
        int cols = board.getCOLS();
        boolean[][] visited = new boolean[rows][cols];
        int[][] dist = new int[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                dist[r][c] = -1;
            }
        }

        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{row, col});
        visited[row][col] = true;
        dist[row][col] = 0;

        // Cardinal directions only: N, S, E, W
        int[][] dirs = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        while (!q.isEmpty()) {
            int[] cur = q.removeFirst();
            int cr = cur[0], cc = cur[1];
            int cd = dist[cr][cc];
            if (cd >= speed) continue; // cannot step further

            for (int[] d : dirs) {
                int nr = cr + d[0];
                int nc = cc + d[1];
                if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                if (visited[nr][nc]) continue;

                // Check if this cell is occupied
                GamePiece gp = board.getGamePieceAtPos(nr, nc);
                boolean isOccupied = gp != null;

                // Can pass through occupied cells (jump over), but cannot end on them
                if (isOccupied) {
                    // Mark as visited and continue pathfinding, but don't add as destination
                    visited[nr][nc] = true;
                    dist[nr][nc] = cd + 1;
                    q.addLast(new int[]{nr, nc});
                    continue;
                }

                // Empty cell - can move here
                visited[nr][nc] = true;
                dist[nr][nc] = cd + 1;
                q.addLast(new int[]{nr, nc});

                // Exclude origin
                if (!(nr == row && nc == col)) {
                    Renderable r = board.getPlotAtPos(nr, nc);
                    if (r instanceof Plot p) {
                        out.add(p);
                    }
                }
            }
        }
        return out;
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

        // Check cardinal direction only
        int rowDiff = Math.abs(plotRow - ownerPos.getRow());
        int colDiff = Math.abs(plotCol - ownerPos.getCol());
        // Must be cardinal direction (one of rowDiff or colDiff must be 0, but not both)
        if (rowDiff > 0 && colDiff > 0) return false; // Diagonal not allowed
        if (rowDiff == 0 && colDiff == 0) return false; // Same position

        // Check if plot is reachable with jump movement
        int speed = owner.getEffectiveSpeed();
        List<Plot> reachable = getJumpReachablePlots(board, ownerPos.getRow(), ownerPos.getCol(), speed);
        for (Plot p : reachable) {
            if (p == plot) {
                // Must be empty (jump can pass through but not end on occupied)
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

        int ownerRow = ownerPos.getRow();
        int ownerCol = ownerPos.getCol();
        int speed = owner.getEffectiveSpeed();
        List<Plot> reachable = getJumpReachablePlots(board, ownerRow, ownerCol, speed);
        // Filter to only empty plots in cardinal directions (jump can pass through but not end on occupied)
        return reachable.stream()
                .filter(plot -> {
                    int[] indices = board.getIndicesOfPlot(plot);
                    if (indices == null) return false;
                    int plotRow = indices[0];
                    int plotCol = indices[1];
                    
                    // Explicitly check cardinal direction only (safety check)
                    int rowDiff = Math.abs(plotRow - ownerRow);
                    int colDiff = Math.abs(plotCol - ownerCol);
                    // Must be cardinal direction (one of rowDiff or colDiff must be 0, but not both)
                    if (rowDiff > 0 && colDiff > 0) return false; // Diagonal not allowed
                    if (rowDiff == 0 && colDiff == 0) return false; // Same position
                    
                    // Must be empty (jump can pass through but not end on occupied)
                    return !board.isOccupied(plotRow, plotCol);
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

        // Reuse validation from isValidTargetForEffect to ensure consistency
        if (!isValidTargetForEffect(destinationPlot, 1)) return;

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

        // Perform the movement with ability name
        boolean moved = MovementUtils.performActiveMovement(
                board,
                owner,
                ownerRow,
                ownerCol,
                destRow,
                destCol,
                "Jump"
        );
        if (!moved) {
            // Movement failed - this shouldn't happen if validation passed
            return;
        }
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

