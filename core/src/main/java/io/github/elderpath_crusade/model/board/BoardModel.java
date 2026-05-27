package io.github.elderpath_crusade.model.board;

import io.github.elderpath_crusade.enums.PieceAlignment;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure game-logic board model. No rendering, no LibGDX dependencies.
 * Manages a grid of pieces and provides query/mutation methods.
 */
public class BoardModel {
    private final int rows;
    private final int cols;
    private final String[][] pieces; // piece IDs; null = empty
    private boolean flipped = false;

    public BoardModel(int rows, int cols) {
        if (rows <= 0 || cols <= 0) throw new IllegalArgumentException("Board dimensions must be positive");
        this.rows = rows;
        this.cols = cols;
        this.pieces = new String[rows][cols];
    }

    public int getRows() { return rows; }
    public int getCols() { return cols; }
    public boolean isFlipped() { return flipped; }
    public void setFlipped(boolean flipped) { this.flipped = flipped; }

    public boolean isValidPosition(int row, int col) {
        return row >= 0 && row < rows && col >= 0 && col < cols;
    }

    public boolean isValidPosition(Position pos) {
        return isValidPosition(pos.row(), pos.col());
    }

    public boolean isOccupied(int row, int col) {
        checkBounds(row, col);
        return pieces[row][col] != null;
    }

    public String getPieceAt(int row, int col) {
        checkBounds(row, col);
        return pieces[row][col];
    }

    public String getPieceAt(Position pos) {
        return getPieceAt(pos.row(), pos.col());
    }

    public void placePiece(int row, int col, String pieceId) {
        checkBounds(row, col);
        if (pieceId == null) throw new IllegalArgumentException("pieceId must not be null");
        if (pieces[row][col] != null) throw new IllegalStateException("Position (" + row + "," + col + ") is occupied");
        pieces[row][col] = pieceId;
    }

    public void removePiece(int row, int col) {
        checkBounds(row, col);
        pieces[row][col] = null;
    }

    public void movePiece(int fromRow, int fromCol, int toRow, int toCol) {
        checkBounds(fromRow, fromCol);
        checkBounds(toRow, toCol);
        String pieceId = pieces[fromRow][fromCol];
        if (pieceId == null) throw new IllegalStateException("No piece at (" + fromRow + "," + fromCol + ")");
        if (pieces[toRow][toCol] != null) throw new IllegalStateException("Destination (" + toRow + "," + toCol + ") is occupied");
        pieces[fromRow][fromCol] = null;
        pieces[toRow][toCol] = pieceId;
    }

    /**
     * Returns the summoning row for the given alignment.
     * P1 summons on row 0 (or ROWS-1 if flipped).
     * P2 summons on row ROWS-1 (or 0 if flipped).
     */
    public int getSummonRow(PieceAlignment alignment) {
        return switch (alignment) {
            case P1 -> flipped ? rows - 1 : 0;
            case P2 -> flipped ? 0 : rows - 1;
            default -> -1;
        };
    }

    public boolean isValidSummonPosition(int row, int col, PieceAlignment alignment) {
        if (!isValidPosition(row, col)) return false;
        if (isOccupied(row, col)) return false;
        return row == getSummonRow(alignment);
    }

    /**
     * Returns cardinal-adjacent positions (N, E, S, W) that are within bounds.
     */
    public List<Position> getAdjacentPositions(int row, int col) {
        List<Position> result = new ArrayList<>(4);
        if (row > 0) result.add(new Position(row - 1, col));
        if (row < rows - 1) result.add(new Position(row + 1, col));
        if (col > 0) result.add(new Position(row, col - 1));
        if (col < cols - 1) result.add(new Position(row, col + 1));
        return result;
    }

    public List<Position> getAdjacentPositions(Position pos) {
        return getAdjacentPositions(pos.row(), pos.col());
    }

    /**
     * Find the position of a piece by its ID. Returns null if not found.
     */
    public Position findPiece(String pieceId) {
        if (pieceId == null) return null;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (pieceId.equals(pieces[r][c])) return new Position(r, c);
            }
        }
        return null;
    }

    private void checkBounds(int row, int col) {
        if (!isValidPosition(row, col))
            throw new IllegalArgumentException("Position (" + row + "," + col + ") out of bounds [" + rows + "x" + cols + "]");
    }
}
