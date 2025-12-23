package io.github.elderpath_crusade.bot.search;

import java.util.Objects;

/**
 * A simple record representing a 2D coordinate on the board.
 * Used for cache keys and passing positions in search algorithms.
 */
public record Coord(int row, int col) {
    @Override
    public String toString() {
        return "(" + row + ", " + col + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Coord coord = (Coord) o;
        return row == coord.row && col == coord.col;
    }

    @Override
    public int hashCode() {
        return Objects.hash(row, col);
    }
}
