package io.github.elderpath_crusade.run;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CardModificationCatalogTest {

    @Test
    void all_isNonEmptyAndEveryEntryHasADelta() {
        assertFalse(CardModificationCatalog.all().isEmpty());
        for (CardModificationCatalog.Entry entry : CardModificationCatalog.all()) {
            assertNotNull(entry.name());
            assertFalse(entry.name().isBlank());
            assertFalse(entry.delta().isEmpty(), entry.name() + " has no actual modification");
        }
    }

    @Test
    void all_everyEntryIsAPureStatDeltaWithNoAddedAbilities() {
        for (CardModificationCatalog.Entry entry : CardModificationCatalog.all()) {
            assertTrue(entry.delta().addedAbilities().isEmpty(),
                    entry.name() + " adds an ability — CONTENT-002's catalog is stat-only, no yaml needed");
        }
    }

    @Test
    void all_namesAreUnique() {
        long distinctCount = CardModificationCatalog.all().stream().map(CardModificationCatalog.Entry::name).distinct().count();
        assertEquals(CardModificationCatalog.all().size(), distinctCount);
    }
}
