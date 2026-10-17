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
 * Regression coverage for JumpMove: it must move in a single straight cardinal line
 * (never a diagonal reached by composing two cardinal steps, which the normal
 * flood-fill movement allows), and it must actually be able to pass over terrain and
 * unit blockers it's flagged to ignore — landing only on an empty tile, never on top
 * of the blocker itself, and never able to pass a blocker type it doesn't ignore.
 */
class GridNavigatorJumpMoveTest {

    private Engine engine;
    private BoardGrid grid;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        engine = GameContext.get().getEcsEngine();
        grid = new BoardGrid(5, 5); // headless — no Board, no Plots
    }

    private static Coord at(int row, int col) {
        return new Coord(row, col);
    }

    private Entity buildMover(PieceAlignment alignment, boolean ignoreTerrain, boolean ignoreFriendly, boolean ignoreHostile) {
        Entity e = engine.createEntity();
        e.add(new AlignmentComponent().set(alignment));
        ComputedStatsComponent computed = new ComputedStatsComponent();
        computed.ignoreTerrainAsBlockers = ignoreTerrain;
        computed.ignoreFriendlyAsBlockers = ignoreFriendly;
        computed.ignoreHostileAsBlockers = ignoreHostile;
        e.add(computed);
        engine.addEntity(e);
        return e;
    }

    private void occupy(int row, int col, PieceAlignment alignment, boolean terrain) {
        Entity occupant = engine.createEntity();
        if (terrain) occupant.add(new TerrainComponent());
        else occupant.add(new AlignmentComponent().set(alignment));
        engine.addEntity(occupant);
        grid.addEntity(row, col, occupant);
    }

    @Test
    void jumpMove_noBlockers_onlyStraightLines_neverDiagonal() {
        Entity mover = buildMover(PieceAlignment.P1, true, true, true);
        List<Coord> reachable = grid.getReachable(mover, 2, 2, 2);

        Set<Coord> expected = Set.of(
                at(0, 2), at(1, 2),
                at(4, 2), at(3, 2),
                at(2, 0), at(2, 1),
                at(2, 4), at(2, 3)
        );
        assertEquals(expected, Set.copyOf(reachable));
        assertFalse(reachable.contains(at(1, 1)), "must not reach a diagonal tile");
        assertFalse(reachable.contains(at(3, 3)), "must not reach a diagonal tile");
    }

    @Test
    void jumpMove_ignoresTerrainAndUnits_landsPastThemNotOnThem() {
        Entity mover = buildMover(PieceAlignment.P1, true, true, true);
        occupy(1, 2, PieceAlignment.NEUTRAL, true);  // terrain directly above
        occupy(3, 2, PieceAlignment.P2, false);       // hostile unit directly below

        List<Coord> reachable = grid.getReachable(mover, 2, 2, 2);

        assertTrue(reachable.contains(at(0, 2)), "should land past the terrain");
        assertFalse(reachable.contains(at(1, 2)), "can't land on the terrain tile itself");
        assertTrue(reachable.contains(at(4, 2)), "should land past the hostile unit");
        assertFalse(reachable.contains(at(3, 2)), "can't land on the unit's tile itself");
    }

    @Test
    void withoutIgnoreFlags_blockedByAnyOccupiedTile() {
        Entity mover = buildMover(PieceAlignment.P1, false, false, false);
        occupy(1, 2, PieceAlignment.NEUTRAL, true);

        List<Coord> reachable = grid.getReachable(mover, 2, 2, 2);

        assertFalse(reachable.contains(at(0, 2)), "a non-jumping piece can't pass through terrain");
        assertFalse(reachable.contains(at(1, 2)));
    }

    @Test
    void cannotPassFriendlyUnit_withoutIgnoreFriendlyFlagSpecifically() {
        Entity mover = buildMover(PieceAlignment.P1, true, false, true); // ignores terrain+hostile, not friendly
        occupy(1, 2, PieceAlignment.P1, false);

        List<Coord> reachable = grid.getReachable(mover, 2, 2, 2);

        assertFalse(reachable.contains(at(0, 2)), "each ignore flag must be checked independently");
    }

    @Test
    void nonJumpingPiece_stillFloodFills_reachingDiagonalsWhenUnobstructed() {
        Entity mover = buildMover(PieceAlignment.P1, false, false, false);
        List<Coord> reachable = grid.getReachable(mover, 2, 2, 2);

        assertTrue(reachable.contains(at(1, 1)), "normal movement is unaffected — flood fill still composes cardinal steps");
        assertTrue(reachable.contains(at(3, 3)));
    }
}
