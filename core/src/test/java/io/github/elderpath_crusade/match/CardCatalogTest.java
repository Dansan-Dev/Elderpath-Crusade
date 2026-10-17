package io.github.elderpath_crusade.match;

import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.data.SpellDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CardCatalogTest {

    @BeforeEach
    void setUp() {
        CardCatalog.clear();
        PieceRegistry.register(new PieceDefinition("WolfCub", 1, 2, 1, 1, 1, List.of()));
        CardCatalog.registerSummon("Wolf Cub", "WolfCub");
        CardCatalog.registerSummon("Wolf", "Wolf");
        CardCatalog.registerSpell("Fireball", new SpellDefinition("Fireball", "", 3, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        CardCatalog.clear();
    }

    @Test
    void summonLookups() {
        assertEquals("WolfCub", CardCatalog.getRegistryKeyForSummon("Wolf Cub"));
        assertEquals(1, CardCatalog.getCost("Wolf Cub"));
        assertNull(CardCatalog.getRegistryKeyForSummon("Fireball"));
    }

    @Test
    void spellCostComesFromDefinition() {
        assertEquals(3, CardCatalog.getCost("Fireball"));
    }

    @Test
    void unknownCard_costIsMinusOne() {
        assertEquals(-1, CardCatalog.getCost("Nope"));
        assertNull(CardCatalog.get(null));
    }

    @Test
    void draftableNames_excludeStarterCards() {
        assertEquals(List.of("Fireball"), CardCatalog.getDraftableNames());
    }
}
