package io.github.elderpath_crusade.match;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.ComputedStatsComponent;
import io.github.elderpath_crusade.ecs.components.TerrainComponent;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TypedEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BoardGrid is the headless logical board — every test here runs with no Board view, no
 * Plots and no GL context, the way a match server will use it.
 */
class BoardGridTest {

    private Engine engine;
    private BoardGrid grid;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        engine = GameContext.get().getEcsEngine();
        grid = new BoardGrid(7, 5);
    }

    private Entity piece(PieceAlignment alignment, int range, int row, int col) {
        Entity e = engine.createEntity();
        e.add(new AlignmentComponent().set(alignment));
        ComputedStatsComponent computed = new ComputedStatsComponent();
        computed.range = range;
        e.add(computed);
        engine.addEntity(e);
        grid.addEntity(row, col, e);
        return e;
    }

    private Entity terrain(int row, int col) {
        Entity e = engine.createEntity();
        e.add(new TerrainComponent());
        engine.addEntity(e);
        grid.addEntity(row, col, e);
        return e;
    }

    @Test
    void constructor_rejectsNonPositiveDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new BoardGrid(0, 5));
        assertThrows(IllegalArgumentException.class, () -> new BoardGrid(5, -1));
    }

    @Test
    void isInBounds_coversEdges() {
        assertTrue(grid.isInBounds(0, 0));
        assertTrue(grid.isInBounds(6, 4));
        assertFalse(grid.isInBounds(-1, 0));
        assertFalse(grid.isInBounds(7, 0));
        assertFalse(grid.isInBounds(0, 5));
    }

    @Test
    void homeAndGoalRows_areAbsolutePerAlignment() {
        assertEquals(0, grid.getHomeRow(PieceAlignment.P1));
        assertEquals(6, grid.getHomeRow(PieceAlignment.P2));
        assertEquals(6, grid.getGoalRow(PieceAlignment.P1));
        assertEquals(0, grid.getGoalRow(PieceAlignment.P2));
        assertEquals(-1, grid.getHomeRow(PieceAlignment.NEUTRAL));
        assertEquals(-1, grid.getGoalRow(PieceAlignment.NEUTRAL));
    }

    @Test
    void isValidSummonTarget_onlyEmptyHomeRowTiles() {
        assertTrue(grid.isValidSummonTarget(0, 2, PieceAlignment.P1));
        assertFalse(grid.isValidSummonTarget(6, 2, PieceAlignment.P1), "P2's home row");
        assertTrue(grid.isValidSummonTarget(6, 2, PieceAlignment.P2));
        assertFalse(grid.isValidSummonTarget(3, 2, PieceAlignment.P1), "mid-board");
        assertFalse(grid.isValidSummonTarget(0, 9, PieceAlignment.P1), "out of bounds");
        assertFalse(grid.isValidSummonTarget(0, 2, PieceAlignment.NEUTRAL));
        assertFalse(grid.isValidSummonTarget(0, 2, null));

        piece(PieceAlignment.P1, 1, 0, 2);
        assertFalse(grid.isValidSummonTarget(0, 2, PieceAlignment.P1), "occupied");
    }

    @Test
    void addMoveRemove_trackOccupancy_andNotifyListener() {
        int[] changes = {0};
        grid.setChangeListener(() -> changes[0]++);

        Entity e = piece(PieceAlignment.P1, 1, 2, 2);
        assertSame(e, grid.getEntityAt(2, 2));
        assertTrue(grid.isOccupied(2, 2));

        grid.moveEntity(2, 2, e, 3, 2);
        assertNull(grid.getEntityAt(2, 2));
        assertSame(e, grid.getEntityAt(3, 2));

        grid.removeEntity(3, 2);
        assertFalse(grid.isOccupied(3, 2));
        assertEquals(3, changes[0], "one notification per add/move/remove");
    }

    @Test
    void addAndRemove_outOfBounds_throw() {
        Entity e = engine.createEntity();
        assertThrows(IllegalArgumentException.class, () -> grid.addEntity(7, 0, e));
        assertThrows(IllegalArgumentException.class, () -> grid.removeEntity(0, -1));
    }

    @Test
    void reachable_floodFill_routesAroundBlockers_withinSpeed() {
        Entity mover = piece(PieceAlignment.P1, 1, 3, 2);
        terrain(4, 2);
        piece(PieceAlignment.P1, 1, 3, 1);     // friendly blocker
        piece(PieceAlignment.P2, 1, 3, 3);     // hostile blocker

        Set<Coord> reachable = Set.copyOf(grid.getReachable(mover, 3, 2, 2));

        assertFalse(reachable.contains(new Coord(4, 2)), "can't land on terrain");
        assertFalse(reachable.contains(new Coord(3, 1)), "can't land on a friendly unit");
        assertFalse(reachable.contains(new Coord(3, 3)), "can't land on a hostile unit");
        assertTrue(reachable.contains(new Coord(2, 2)));
        assertTrue(reachable.contains(new Coord(2, 1)), "diagonal reached by two cardinal steps");
        assertFalse(reachable.contains(new Coord(5, 2)), "can't pass through terrain without the flag");
        assertFalse(reachable.contains(new Coord(3, 2)), "never includes the mover's own tile");
        assertFalse(reachable.contains(new Coord(0, 2)), "beyond speed 2");
    }

    @Test
    void reachable_zeroSpeed_isEmpty() {
        Entity mover = piece(PieceAlignment.P1, 1, 3, 2);
        assertTrue(grid.getReachable(mover, 3, 2, 0).isEmpty());
    }

    @Test
    void attackableEnemies_rangeScansStopAtFirstOccupant() {
        piece(PieceAlignment.P1, 2, 3, 2);           // attacker, range 2
        piece(PieceAlignment.P2, 1, 1, 2);           // 2 tiles up — in range
        piece(PieceAlignment.P1, 1, 3, 3);           // friendly right next to it — blocks
        piece(PieceAlignment.P2, 1, 3, 4);           // behind the friendly — hidden
        terrain(4, 2);                               // terrain below — blocks, not a target
        piece(PieceAlignment.P2, 1, 5, 2);           // behind the terrain — hidden
        piece(PieceAlignment.NEUTRAL, 1, 3, 1);      // neutral — never a target

        List<Coord> targets = grid.getAttackableEnemies(3, 2, PieceAlignment.P1);

        assertEquals(List.of(new Coord(1, 2)), targets);
    }

    @Test
    void attackableEnemies_rangeZeroStillHitsAdjacent_negativeRangeCannotAttack() {
        piece(PieceAlignment.P1, 0, 3, 2);
        piece(PieceAlignment.P2, 1, 2, 2);
        assertEquals(List.of(new Coord(2, 2)), grid.getAttackableEnemies(3, 2, PieceAlignment.P1));

        GameContext.create(); // fresh engine + grid index
        engine = GameContext.get().getEcsEngine();
        grid = new BoardGrid(7, 5);
        piece(PieceAlignment.P1, -1, 3, 2);
        piece(PieceAlignment.P2, 1, 2, 2);
        assertTrue(grid.getAttackableEnemies(3, 2, PieceAlignment.P1).isEmpty());
    }

    @Test
    void attackableEnemies_noPieceAtSource_isEmpty() {
        assertTrue(grid.getAttackableEnemies(3, 2, PieceAlignment.P1).isEmpty());
    }

    @Test
    void adjacentHostiles_excludeFriendlyNeutralAndDiagonal() {
        piece(PieceAlignment.P2, 1, 2, 2);
        piece(PieceAlignment.P1, 1, 3, 1);
        piece(PieceAlignment.NEUTRAL, 1, 3, 3);
        piece(PieceAlignment.P2, 1, 4, 3); // diagonal

        assertEquals(List.of(new Coord(2, 2)), grid.getAdjacentHostiles(3, 2, PieceAlignment.P1));
    }

    @Test
    void adjacentHostiles_atCorner_staysInBounds() {
        piece(PieceAlignment.P2, 1, 0, 1);
        assertEquals(List.of(new Coord(0, 1)), grid.getAdjacentHostiles(0, 0, PieceAlignment.P1));
    }
}
