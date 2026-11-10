package io.github.elderpath_crusade.collection;

import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.run.RunDifficulty;
import io.github.elderpath_crusade.run.RunMap;
import io.github.elderpath_crusade.run.RunState;
import io.github.elderpath_crusade.utils.GameVersion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CollectionManager#recordWin} against a real (if minimal) {@link RunState} — a fake
 * in-memory {@link DeckCollectionStore} so this stays off disk, same bar as every other
 * headless test here.
 */
class CollectionManagerTest {

    private static class InMemoryStore implements DeckCollectionStore {
        final List<DeckCollectionEntry> saved = new ArrayList<>();

        @Override
        public List<DeckCollectionEntry> loadAll() {
            return List.copyOf(saved);
        }

        @Override
        public void save(DeckCollectionEntry entry) {
            saved.add(entry);
        }
    }

    private static RunState wonRun(int difficultyLevel) {
        RunState run = RunState.start(RunMap.linearChain(1), new RunDifficulty(difficultyLevel),
                List.of("Wolf", "Wolf", "Wolf Cub", "Wolf Cub"), 1);
        run.addRelic("PackLeader");
        run.endRun(true);
        return run;
    }

    @Test
    void recordWin_savesAnEntryWithTheRunsDeckElderRelicsAndMetadata() {
        InMemoryStore store = new InMemoryStore();
        CollectionManager manager = new CollectionManager(store);
        RunState run = wonRun(3);

        DeckCollectionEntry entry = manager.recordWin(run, GameMode.ROUGELIKE_RUN);

        assertEquals(List.of(entry), store.saved);
        assertEquals(GameMode.ROUGELIKE_RUN, entry.mode());
        assertEquals(3, entry.difficultyLevel());
        assertEquals(run.deck(), entry.deck());
        assertEquals(run.elders(), entry.elders());
        assertEquals(List.of("PackLeader"), entry.relicIds());
        assertEquals(run.durationMillis(), entry.runCompletionTimeMillis());
        assertEquals(GameVersion.current(), entry.gameVersion());
        assertNotNull(entry.id());
        assertFalse(entry.id().isBlank());
    }

    @Test
    void recordWin_eachCallGetsADistinctId() {
        InMemoryStore store = new InMemoryStore();
        CollectionManager manager = new CollectionManager(store);

        DeckCollectionEntry first = manager.recordWin(wonRun(1), GameMode.ROUGELIKE_RUN);
        DeckCollectionEntry second = manager.recordWin(wonRun(1), GameMode.ROUGELIKE_RUN);

        assertNotEquals(first.id(), second.id());
        assertEquals(2, manager.all().size());
    }

    @Test
    void recordWin_rejectsARunThatHasNotEnded() {
        CollectionManager manager = new CollectionManager(new InMemoryStore());
        RunState run = RunState.start(RunMap.linearChain(1), RunDifficulty.DEFAULT, List.of("Wolf"), 1);
        assertThrows(IllegalStateException.class, () -> manager.recordWin(run, GameMode.ROUGELIKE_RUN));
    }

    @Test
    void recordWin_rejectsARunThatEndedInALoss() {
        CollectionManager manager = new CollectionManager(new InMemoryStore());
        RunState run = RunState.start(RunMap.linearChain(1), RunDifficulty.DEFAULT, List.of("Wolf"), 1);
        run.endRun(false);
        assertThrows(IllegalStateException.class, () -> manager.recordWin(run, GameMode.ROUGELIKE_RUN));
    }

    @Test
    void all_delegatesToTheStore() {
        InMemoryStore store = new InMemoryStore();
        CollectionManager manager = new CollectionManager(store);
        assertEquals(List.of(), manager.all());
        manager.recordWin(wonRun(1), GameMode.ROUGELIKE_RUN);
        assertEquals(1, manager.all().size());
    }

    @Test
    void defaultFile_isUnderTheUserHomeDirectory_notInsideAssetsOrTheBuildDir() {
        String home = System.getProperty("user.home");
        assertTrue(CollectionManager.defaultFile().getPath().startsWith(home));
    }
}
