package io.github.elderpath_crusade.game_objects.board.components;

import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.Renderable;
import lombok.Getter;

/**
 * Handles the physical perspective of the board, including row flipping for
 * LOCAL_MATCH mode.
 */
public class BoardPerspectiveManager {
    private final Board board;
    @Getter
    private boolean physicallyFlipped = false;

    public BoardPerspectiveManager(Board board) {
        this.board = board;
    }

    /**
     * Physically flip the board by swapping rows in the board and gamePieces
     * arrays.
     * Updates all plot bounds and GamePiece POSITION data to reflect new positions.
     */
    public void flipRows() {
        int rows = board.getROWS();
        int cols = board.getCOLS();
        int plotWidth = board.getPLOT_WIDTH();
        int plotHeight = board.getPLOT_HEIGHT();
        Renderable[][] layout = board.getLayout();
        GamePiece[][] pieces = board.getGamePieces();

        // Swap plots in layout array (visual)
        for (int row = 0; row < rows / 2; row++) {
            int swapRow = rows - 1 - row;

            Renderable[] tempRow = layout[row];
            layout[row] = layout[swapRow];
            layout[swapRow] = tempRow;

            for (int col = 0; col < cols; col++) {
                Renderable plot = layout[row][col];
                if (plot != null && plot.getBounds() != null) {
                    plot.getBounds().setX(col * plotWidth);
                    plot.getBounds().setY(row * plotHeight);
                    if (plot instanceof Plot p) p.setGridPos(row, col);
                }
                Renderable swapPlot = layout[swapRow][col];
                if (swapPlot != null && swapPlot.getBounds() != null) {
                    swapPlot.getBounds().setX(col * plotWidth);
                    swapPlot.getBounds().setY(swapRow * plotHeight);
                    if (swapPlot instanceof Plot p) p.setGridPos(swapRow, col);
                }
            }
        }

        // Swap gamePieces array rows (write-cache) and update positions
        for (int row = 0; row < rows / 2; row++) {
            int swapRow = rows - 1 - row;
            GamePiece[] tempPieces = pieces[row];
            pieces[row] = pieces[swapRow];
            pieces[swapRow] = tempPieces;
        }

        // Update all piece positions to match new array layout
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                GamePiece gp = pieces[row][col];
                if (gp != null) {
                    Object posObj = gp.getData(GamePieceData.POSITION);
                    if (posObj instanceof Board.Position pos) {
                        pos.setRow(row);
                        pos.setCol(col);
                    }
                }
            }
        }

        // Rebuild GridIndexSystem to match new positions
        io.github.elderpath_crusade.ecs.systems.GridIndexSystem gridIndex =
                io.github.elderpath_crusade.GameContext.get().getEcsEngine()
                        .getSystem(io.github.elderpath_crusade.ecs.systems.GridIndexSystem.class);
        if (gridIndex != null) {
            gridIndex.clear();
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    GamePiece gp = pieces[row][col];
                    if (gp != null && gp instanceof io.github.elderpath_crusade.game_objects.board.MonsterGamePiece mgp) {
                        com.badlogic.ashley.core.Entity entity = mgp.getEntity();
                        if (entity != null) {
                            gridIndex.onEntitySpawned(entity, row, col);
                        }
                    }
                }
            }
        }

        physicallyFlipped = !physicallyFlipped;
        board.markDirtyAndNotify();
    }

    public void reset() {
        physicallyFlipped = false;
    }
}
