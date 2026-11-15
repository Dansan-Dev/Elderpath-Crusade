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

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Ordinary (flood-fill) movement, attack range and adjacency on a BoardGrid; jump moves live in GridNavigatorJumpMoveTest. */
class GridNavigatorTest {

    private Engine engine;
    private BoardGrid grid;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        engine = GameContext.get().getEcsEngine();
        grid = new BoardGrid(5, 5);
    }

    private static Coord at(int row, int col) {
        return new Coord(row, col);
    }

    private Entity place(int row, int col, PieceAlignment alignment, int range) {
        Entity e = engine.createEntity();
        e.add(new AlignmentComponent().set(alignment));
        ComputedStatsComponent computed = new ComputedStatsComponent();
        computed.range = range;
        e.add(computed);
        engine.addEntity(e);
        grid.addEntity(row, col, e);
        return e;
    }

    private void terrain(int row, int col) {
        Entity e = engine.createEntity();
        e.add(new TerrainComponent());
        engine.addEntity(e);
        grid.addEntity(row, col, e);
    }

    @Test
    void reachable_speedOne_isTheFourNeighbours() {
        Entity mover = place(2, 2, PieceAlignment.P1, 1);
        assertEquals(Set.of(at(1, 2), at(3, 2), at(2, 1), at(2, 3)),
                Set.copyOf(grid.getReachable(mover, 2, 2, 1)));
    }

    @Test
    void reachable_speedZero_isEmpty() {
        Entity mover = place(2, 2, PieceAlignment.P1, 1);
        assertTrue(grid.getReachable(mover, 2, 2, 0).isEmpty());
    }

    @Test
    void reachable_speedTwo_includesDiagonalsByTwoCardinalSteps() {
        Entity mover = place(2, 2, PieceAlignment.P1, 1);
        Set<Coord> reachable = Set.copyOf(grid.getReachable(mover, 2, 2, 2));
        assertTrue(reachable.contains(at(1, 1)));
        assertTrue(reachable.contains(at(0, 2)));
        assertFalse(reachable.contains(at(2, 2)), "never its own tile");
        assertFalse(reachable.contains(at(0, 0)), "three steps away");
    }

    @Test
    void reachable_walksAroundObstacleButNotThroughIt() {
        Entity mover = place(0, 0, PieceAlignment.P1, 1);
        terrain(0, 1);
        terrain(1, 0);
        // fully walled in at the corner
        assertTrue(grid.getReachable(mover, 0, 0, 3).isEmpty());
    }

    @Test
    void reachable_neverIncludesOccupiedTiles() {
        Entity mover = place(2, 2, PieceAlignment.P1, 1);
        place(2, 3, PieceAlignment.P2, 1);
        place(1, 2, PieceAlignment.P1, 1);
        Set<Coord> reachable = Set.copyOf(grid.getReachable(mover, 2, 2, 1));
        assertFalse(reachable.contains(at(2, 3)));
        assertFalse(reachable.contains(at(1, 2)));
        assertEquals(2, reachable.size());
    }

    @Test
    void reachable_staysInBoundsAtTheEdge() {
        Entity mover = place(0, 0, PieceAlignment.P1, 1);
        assertEquals(Set.of(at(1, 0), at(0, 1)), Set.copyOf(grid.getReachable(mover, 0, 0, 1)));
    }

    @Test
    void attackable_rangeOne_adjacentEnemyOnly() {
        place(2, 2, PieceAlignment.P1, 1);
        place(2, 3, PieceAlignment.P2, 1);
        place(1, 2, PieceAlignment.P1, 1);
        assertEquals(Set.of(at(2, 3)), Set.copyOf(grid.getAttackableEnemies(2, 2, PieceAlignment.P1)));
    }

    @Test
    void attackable_rangeTwo_reachesPastAnEmptyTile() {
        place(2, 2, PieceAlignment.P1, 2);
        place(2, 4, PieceAlignment.P2, 1);
        assertEquals(Set.of(at(2, 4)), Set.copyOf(grid.getAttackableEnemies(2, 2, PieceAlignment.P1)));
    }

    @Test
    void attackable_anyPieceBlocksTheLineBehindIt() {
        place(2, 2, PieceAlignment.P1, 3);
        place(2, 3, PieceAlignment.P1, 1); // friendly blocker
        place(2, 4, PieceAlignment.P2, 1);
        assertTrue(grid.getAttackableEnemies(2, 2, PieceAlignment.P1).isEmpty());
    }

    @Test
    void attackable_terrainBlocksAndIsNeverATarget() {
        place(2, 2, PieceAlignment.P1, 3);
        terrain(2, 3);
        place(2, 4, PieceAlignment.P2, 1);
        assertTrue(grid.getAttackableEnemies(2, 2, PieceAlignment.P1).isEmpty());
    }

    @Test
    void attackable_negativeRange_cannotAttackAtAll() {
        place(2, 2, PieceAlignment.P1, -1);
        place(2, 3, PieceAlignment.P2, 1);
        assertTrue(grid.getAttackableEnemies(2, 2, PieceAlignment.P1).isEmpty());
    }

    @Test
    void attackable_zeroRange_stillHitsAdjacent() {
        place(2, 2, PieceAlignment.P1, 0);
        place(2, 3, PieceAlignment.P2, 1);
        assertEquals(Set.of(at(2, 3)), Set.copyOf(grid.getAttackableEnemies(2, 2, PieceAlignment.P1)));
    }

    @Test
    void attackable_emptySourceTile_isEmpty() {
        assertTrue(grid.getAttackableEnemies(2, 2, PieceAlignment.P1).isEmpty());
    }

    @Test
    void adjacentHostiles_listsEnemiesOnlyNotFriendsOrTerrain() {
        place(2, 2, PieceAlignment.P1, 1);
        place(2, 3, PieceAlignment.P2, 1);
        place(3, 2, PieceAlignment.P1, 1);
        terrain(1, 2);
        assertEquals(Set.of(at(2, 3)), Set.copyOf(grid.getAdjacentHostiles(2, 2, PieceAlignment.P1)));
    }

    @Test
    void adjacentHostiles_atCorner_doesNotLeaveTheBoard() {
        place(0, 0, PieceAlignment.P1, 1);
        place(0, 1, PieceAlignment.P2, 1);
        assertEquals(Set.of(at(0, 1)), Set.copyOf(grid.getAdjacentHostiles(0, 0, PieceAlignment.P1)));
    }
}
