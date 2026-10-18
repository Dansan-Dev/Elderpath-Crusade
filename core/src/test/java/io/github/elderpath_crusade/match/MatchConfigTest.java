package io.github.elderpath_crusade.match;

import io.github.elderpath_crusade.enums.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MatchConfigTest {

    /** DESIGN-001 regression: the standard mountains sat on row 2, one row closer to P1's home row. */
    @Test
    void standardTerrain_isOnTheCenterRow() {
        MatchConfig config = MatchConfig.standard(GameMode.DEMO, List.of(), List.of(), null, Map.of());
        int centerRow = config.rows() / 2;
        assertFalse(config.terrain().isEmpty());
        for (Coord c : config.terrain()) {
            assertEquals(centerRow, c.row(), "terrain " + c + " is off the center row");
        }
    }

    @Test
    void standardTerrain_isTheSameFromBothSides() {
        MatchConfig config = MatchConfig.standard(GameMode.DEMO, List.of(), List.of(), null, Map.of());
        for (Coord c : config.terrain()) {
            Coord mirrored = new Coord(config.rows() - 1 - c.row(), c.col());
            assertTrue(config.terrain().contains(mirrored), "no mirror of " + c + " for the other side");
        }
    }
}
