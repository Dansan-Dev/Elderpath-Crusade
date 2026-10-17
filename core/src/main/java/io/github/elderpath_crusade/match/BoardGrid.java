package io.github.elderpath_crusade.match;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.systems.GridIndexSystem;
import io.github.elderpath_crusade.enums.PieceAlignment;

import java.util.List;

/**
 * The logical board: dimensions, occupancy and navigation in absolute coordinates, with no
 * rendering or Plot dependency — usable headlessly by a match server. Occupancy is backed
 * by GridIndexSystem. The renderable Board wraps one of these and only maps its coordinates
 * to Plots; flipping the board (hotseat) is view-only and never changes these coordinates.
 *
 * Coordinates are absolute: P1's home (summon) row is row 0 and its goal row is ROWS-1;
 * P2's home row is ROWS-1 and its goal row is 0.
 */
public class BoardGrid {
    private final int rows;
    private final int cols;
    private final GridNavigator navigator;
    private GridIndexSystem gridIndex;
    private Runnable changeListener;

    public BoardGrid(int rows, int cols) {
        if (rows <= 0 || cols <= 0) {
            throw new IllegalArgumentException("rows and cols must be positive, got " + rows + "x" + cols);
        }
        this.rows = rows;
        this.cols = cols;
        this.navigator = new GridNavigator(this);
    }

    /** Called after every occupancy change — lets a board view refresh its render state. */
    public void setChangeListener(Runnable listener) {
        this.changeListener = listener;
    }

    public int getRows() { return rows; }

    public int getCols() { return cols; }

    public boolean isInBounds(int row, int col) {
        return row >= 0 && row < rows && col >= 0 && col < cols;
    }

    public Entity getEntityAt(int row, int col) {
        GridIndexSystem gi = getGridIndex();
        return gi != null ? gi.getEntityAt(row, col) : null;
    }

    public boolean isOccupied(int row, int col) {
        return getEntityAt(row, col) != null;
    }

    /** The row an alignment summons onto, or -1 for NEUTRAL. */
    public int getHomeRow(PieceAlignment alignment) {
        if (alignment == PieceAlignment.P1) return 0;
        if (alignment == PieceAlignment.P2) return rows - 1;
        return -1;
    }

    /** The row an alignment wins by reaching (the opponent's home row), or -1 for NEUTRAL. */
    public int getGoalRow(PieceAlignment alignment) {
        if (alignment == PieceAlignment.P1) return rows - 1;
        if (alignment == PieceAlignment.P2) return 0;
        return -1;
    }

    public boolean isValidSummonTarget(int row, int col, PieceAlignment alignment) {
        if (alignment == null || !isInBounds(row, col)) return false;
        if (isOccupied(row, col)) return false;
        return row == getHomeRow(alignment);
    }

    public void addEntity(int row, int col, Entity entity) {
        checkInBounds(row, col);
        GridIndexSystem gi = getGridIndex();
        if (entity != null && gi != null) gi.onEntitySpawned(entity, row, col);
        notifyChanged();
    }

    public void moveEntity(int fromRow, int fromCol, Entity entity, int toRow, int toCol) {
        GridIndexSystem gi = getGridIndex();
        if (entity != null && gi != null) gi.onEntityMoved(fromRow, fromCol, entity, toRow, toCol);
        notifyChanged();
    }

    public void removeEntity(int row, int col) {
        checkInBounds(row, col);
        GridIndexSystem gi = getGridIndex();
        if (gi != null) gi.onEntityDied(row, col);
        notifyChanged();
    }

    /** Tiles the mover can reach with one move action — see GridNavigator. */
    public List<Coord> getReachable(Entity mover, int row, int col, int speed) {
        return navigator.getReachable(mover, row, col, speed);
    }

    /** Hostile pieces the piece at (row, col) can attack — see GridNavigator. */
    public List<Coord> getAttackableEnemies(int row, int col, PieceAlignment friendlyAlignment) {
        return navigator.getAttackableEnemies(row, col, friendlyAlignment);
    }

    /** Hostile (non-neutral) pieces orthogonally adjacent to (row, col). */
    public List<Coord> getAdjacentHostiles(int row, int col, PieceAlignment friendlyAlignment) {
        return navigator.getAdjacentHostiles(row, col, friendlyAlignment);
    }

    private void notifyChanged() {
        if (changeListener != null) changeListener.run();
    }

    private void checkInBounds(int row, int col) {
        if (!isInBounds(row, col)) {
            throw new IllegalArgumentException(
                    "row must be in [0, " + (rows - 1) + "] and col must be in [0, " + (cols - 1) + "]");
        }
    }

    private GridIndexSystem getGridIndex() {
        if (gridIndex == null) {
            gridIndex = GameContext.get().getEcsEngine().getSystem(GridIndexSystem.class);
        }
        return gridIndex;
    }
}
