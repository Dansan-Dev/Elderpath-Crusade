package io.github.elderpath_crusade.ui_objects;

import io.github.elderpath_crusade.data.RelicRegistry;
import io.github.elderpath_crusade.run.RelicDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** ART-003: a relic carrier's ability bubble finds its relic (and so its icon) by ability id. */
class RelicHudTest {

    @BeforeEach
    void setUp() {
        RelicRegistry.clear();
        RelicRegistry.register(RelicDefinition.battleOnly("Horn", "blast", List.of("HornBlast")));
        RelicRegistry.register(RelicDefinition.battleOnly("Bonfire", "warmth", List.of("BonfireWarmth")));
    }

    @AfterEach
    void tearDown() {
        RelicRegistry.clear();
    }

    @Test
    void relicIdForAbility_findsTheRelicCarryingIt() {
        assertEquals("Horn", RelicHud.relicIdForAbility("HornBlast"));
        assertEquals("Bonfire", RelicHud.relicIdForAbility("BonfireWarmth"));
    }

    @Test
    void relicIdForAbility_unknownOrNull_isNull() {
        assertNull(RelicHud.relicIdForAbility("PackHunter"));
        assertNull(RelicHud.relicIdForAbility(null));
    }
}
