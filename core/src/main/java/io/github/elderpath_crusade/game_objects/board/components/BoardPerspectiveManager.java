package io.github.elderpath_crusade.game_objects.board.components;

import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.game_piece.GamePiece;
import io.github.elderpath_crusade.game_objects.board.plot.Plot;
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

        // Swap plots and game pieces in the arrays
        for (int row = 0; row < rows / 2; row++) {
            int swapRow = rows - 1 - row;

            // Swap plots in board array
            Renderable[] tempRow = layout[row];
            layout[row] = layout[swapRow];
            layout[swapRow] = tempRow;

            // Swap game pieces in gamePieces array
            GamePiece[] tempPieces = pieces[row];
            pieces[row] = pieces[swapRow];
            pieces[swapRow] = tempPieces;

            // Update bounds for swapped plots in both rows
            for (int col = 0; col < cols; col++) {
                // Update plot bounds to match new row positions
                Renderable plot = layout[row][col];
                if (plot != null && plot.getBounds() != null) {
                    plot.getBounds().setX(col * plotWidth);
                    plot.getBounds().setY(row * plotHeight);
                    if (plot instanceof Plot p)
                        p.setGridPos(row, col);
                }

                Renderable swapPlot = layout[swapRow][col];
                if (swapPlot != null && swapPlot.getBounds() != null) {
                    swapPlot.getBounds().setX(col * plotWidth);
                    swapPlot.getBounds().setY(swapRow * plotHeight);
                    if (swapPlot instanceof Plot p)
                        p.setGridPos(swapRow, col);
                }
            }
        }

        // Update all game pieces' POSITION data to reflect new row positions
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                GamePiece gp = pieces[row][col];
                if (gp != null) {
                    gp.updateData(GamePieceData.POSITION, new Board.Position(board, row, col));
                }
            }
        }

        // Toggle the tracked flip state
        physicallyFlipped = !physicallyFlipped;

        // Notify z-index registry that board structure changed
        board.markDirtyAndNotify();
    }

    public void reset() {
        physicallyFlipped = false;
    }
}
