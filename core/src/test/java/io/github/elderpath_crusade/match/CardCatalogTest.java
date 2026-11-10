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

    @Test
    void isSummon_trueOnlyForARegisteredSummon() {
        // BUG-045: a spell has no PieceDefinition — can't be an Elder or a card-mod target.
        assertTrue(CardCatalog.isSummon("Wolf Cub"));
        assertFalse(CardCatalog.isSummon("Fireball"));
        assertFalse(CardCatalog.isSummon("Nope"));
        assertFalse(CardCatalog.isSummon(null));
    }

    @Test
    void draftableNames_excludeASyntheticRunInstanceCard() {
        // BUG-048: a finished run's modified card (e.g. "Crow#run-7") stays registered in this
        // process-lifetime catalog forever (initialize() only ever runs once, at app start) —
        // it must never leak into a later draft pool (classic DraftRoom, or a brand new run's
        // own RunDraftRoom/RunManager pool).
        CardCatalog.registerSummon(SyntheticCardNames.encode("Wolf Cub", "run-7"), "WolfCub");

        assertEquals(List.of("Fireball"), CardCatalog.getDraftableNames());
    }
}
