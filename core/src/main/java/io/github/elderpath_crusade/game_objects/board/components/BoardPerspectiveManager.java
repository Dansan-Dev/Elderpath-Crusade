package io.github.elderpath_crusade.game_objects.board.components;

import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.interfaces.Renderable;
import lombok.Getter;

/**
 * Handles the board's view perspective: in LOCAL_MATCH (hotseat) the view is mirrored
 * vertically on P2's turn so the current player's home row is always at the bottom.
 *
 * View-only: mirroring repositions plots on screen, but never changes logical coordinates
 * (layout indices, Plot grid positions, ECS PositionComponents, GridIndexSystem) — those are
 * absolute, so game logic and a headless match server never need to know about it.
 */
public class BoardPerspectiveManager {
    private final Board board;
    @Getter
    private boolean flipped = false;

    public BoardPerspectiveManager(Board board) {
        this.board = board;
    }

    /** Toggles the mirrored view and moves every plot to its new screen row. */
    public void flipRows() {
        flipped = !flipped;

        int plotHeight = board.getPLOT_HEIGHT();
        Renderable[][] layout = board.getLayout();
        for (int row = 0; row < board.getROWS(); row++) {
            int displayY = board.toDisplayRow(row) * plotHeight;
            for (int col = 0; col < board.getCOLS(); col++) {
                Renderable plot = layout[row][col];
                if (plot != null && plot.getBounds() != null) plot.getBounds().setY(displayY);
            }
        }
        board.markDirtyAndNotify();
    }

    public void reset() {
        flipped = false;
    }
}
