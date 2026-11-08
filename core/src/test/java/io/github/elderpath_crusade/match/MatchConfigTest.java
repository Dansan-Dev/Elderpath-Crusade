package io.github.elderpath_crusade.match;

import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.run.RunCardModification;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MatchConfigTest {

    @Test
    void standard_hasNoRelicsOrCardModsByDefault() {
        MatchConfig config = MatchConfig.standard(GameMode.DEMO, List.of("Wolf"), List.of("Wolf"), null, Map.of());
        assertEquals(List.of(), config.relics(PieceAlignment.P1));
        assertEquals(Map.of(), config.cardMods(PieceAlignment.P1));
    }

    @Test
    void withRelics_and_withCardMods_areIndependentWithers() {
        MatchConfig config = MatchConfig.standard(GameMode.DEMO, List.of("Wolf"), List.of("Wolf"), null, Map.of())
                .withRelics(Map.of(PieceAlignment.P1, List.of("PackLeader")))
                .withCardMods(Map.of(PieceAlignment.P1,
                        Map.of("run-0", new RunCardModification(Map.of("damage", 1), List.of()))));

        assertEquals(List.of("PackLeader"), config.relics(PieceAlignment.P1));
        assertEquals(List.of(), config.relics(PieceAlignment.P2));
        assertEquals(1, config.cardMods(PieceAlignment.P1).get("run-0").statDeltas().get("damage"));
    }

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
