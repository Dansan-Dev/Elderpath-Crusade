package io.github.elderpath_crusade.model.board;

/**
 * Immutable grid position on a board.
 */
public record Position(int row, int col) {

    public boolean isValid(int rows, int cols) {
        return row >= 0 && row < rows && col >= 0 && col < cols;
    }

    public int manhattanDistance(Position other) {
        return Math.abs(row - other.row) + Math.abs(col - other.col);
    }

    public int chebyshevDistance(Position other) {
        return Math.max(Math.abs(row - other.row), Math.abs(col - other.col));
    }
}
