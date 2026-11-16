package io.github.elderpath_crusade.bot.eval;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.bot.search.ThreatMap;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.ecs.components.TerrainComponent;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.match.BoardGrid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The tiles an enemy side can hit next turn: its neighbours, plus everywhere it can walk to first. */
class BotUtilsTest {
    private Engine engine;
    private BoardGrid grid;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        engine = GameContext.get().getEcsEngine();
        grid = new BoardGrid(7, 5);
    }

    private Entity piece(int row, int col, PieceAlignment side, int damage, int speed, int actions) {
        Entity e = engine.createEntity();
        e.add(new AlignmentComponent().set(side));
        StatsComponent stats = new StatsComponent().set(1, 3, damage, speed, actions);
        stats.remainingActions = actions;
        e.add(stats);
        engine.addEntity(e);
        grid.addEntity(row, col, e);
        return e;
    }

    @Test
    void anEnemyWithOneActionThreatensOnlyItsNeighbours() {
        piece(3, 2, PieceAlignment.P2, 1, 2, 1); // one action: it can attack but not walk first
        ThreatMap map = BotUtils.computeThreatMap(grid, PieceAlignment.P2);
        assertTrue(map.isThreatened(2, 2));
        assertTrue(map.isThreatened(4, 2));
        assertTrue(map.isThreatened(3, 1));
        assertTrue(map.isThreatened(3, 3));
        assertFalse(map.isThreatened(1, 2), "two tiles away is out of reach without a move");
    }

    @Test
    void anEnemyWithAnExtraActionAlsoThreatensWhereItCanWalkTo() {
        piece(3, 2, PieceAlignment.P2, 1, 2, 2); // walk 2, then attack
        ThreatMap map = BotUtils.computeThreatMap(grid, PieceAlignment.P2);
        assertTrue(map.isThreatened(1, 2), "two steps up, then adjacent");
        assertTrue(map.isThreatened(0, 2), "reaching row 1... and hitting row 0 beyond");
        assertFalse(map.isThreatened(0, 0));
    }

    @Test
    void obstaclesBlockTheWalkButNotTheAttackOntoThem() {
        piece(3, 2, PieceAlignment.P2, 1, 1, 2);
        Entity wall = engine.createEntity();
        wall.add(new TerrainComponent());
        engine.addEntity(wall);
        grid.addEntity(2, 2, wall);
        ThreatMap map = BotUtils.computeThreatMap(grid, PieceAlignment.P2);
        assertTrue(map.isThreatened(2, 2), "a tile next to the enemy is threatened even if occupied");
        assertFalse(map.isThreatened(1, 2), "it cannot walk through the wall to get closer");
    }

    @Test
    void piecesThatCannotHitAreNoThreat_andOnlyTheNamedSideCounts() {
        piece(3, 2, PieceAlignment.P2, 0, 2, 2);      // no damage
        piece(5, 2, PieceAlignment.P1, 5, 2, 2);      // the other side
        Entity exhausted = piece(1, 4, PieceAlignment.P2, 3, 2, 0); // no actions
        assertNotNull(exhausted);
        ThreatMap map = BotUtils.computeThreatMap(grid, PieceAlignment.P2);
        for (int r = 0; r < 7; r++) for (int c = 0; c < 5; c++) assertFalse(map.isThreatened(r, c), r + "," + c);
    }

    @Test
    void overlappingThreatsAreCounted() {
        piece(3, 1, PieceAlignment.P2, 1, 1, 1);
        piece(3, 3, PieceAlignment.P2, 1, 1, 1);
        ThreatMap map = BotUtils.computeThreatMap(grid, PieceAlignment.P2);
        assertEquals(2, map.getCount(3, 2), "both enemies can hit the tile between them");
        assertEquals(1, map.getCount(3, 0));
    }
}
