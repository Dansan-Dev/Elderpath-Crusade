package io.github.elderpath_crusade.collection;

import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.run.RunCardInstance;
import io.github.elderpath_crusade.run.RunCardModification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Plain-{@link File}-backed, so no Gdx/GameContext needed — a temp file round-trips exactly. */
class JsonDeckCollectionStoreTest {

    private static DeckCollectionEntry sampleEntry(String id) {
        return new DeckCollectionEntry(id, GameMode.ROUGELIKE_RUN, 3, 123_456L, 1_700_000_000_000L, "1.0.0",
                List.of(RunCardInstance.unmodified("run-0", "Wolf"),
                        new RunCardInstance("run-1", "Wolf Cub", new RunCardModification(Map.of("damage", 2), List.of("Frenzy")))),
                List.of(RunCardInstance.unmodified("run-2", "Rogue")),
                List.of("PackLeader"));
    }

    @Test
    void loadAll_withNoFileYet_isEmpty(@TempDir File dir) {
        DeckCollectionStore store = new JsonDeckCollectionStore(new File(dir, "collection.json"));
        assertEquals(List.of(), store.loadAll());
    }

    @Test
    void save_thenLoadAll_roundTripsExactly(@TempDir File dir) {
        File file = new File(dir, "nested/collection.json");
        DeckCollectionStore store = new JsonDeckCollectionStore(file);
        DeckCollectionEntry entry = sampleEntry("run-win-1");

        store.save(entry);

        assertTrue(file.isFile(), "save() should create the file (and its parent dir)");
        assertEquals(List.of(entry), store.loadAll());
    }

    @Test
    void save_appendsRatherThanOverwriting(@TempDir File dir) {
        DeckCollectionStore store = new JsonDeckCollectionStore(new File(dir, "collection.json"));
        DeckCollectionEntry first = sampleEntry("first");
        DeckCollectionEntry second = sampleEntry("second");

        store.save(first);
        store.save(second);

        assertEquals(List.of(first, second), store.loadAll());
    }
}
