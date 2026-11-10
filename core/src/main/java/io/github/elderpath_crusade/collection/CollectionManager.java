package io.github.elderpath_crusade.collection;

import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.run.RunState;
import io.github.elderpath_crusade.utils.GameVersion;

import java.io.File;
import java.util.List;
import java.util.UUID;

/**
 * {@code GameContext}-held (constructed once, every context kind, like {@code RunManager}) —
 * the player's persistent deck collection (Sprint 64): winning a roguelike run snapshots its
 * deck, Elders and active relics here, alongside metadata about the run. Delegates storage to
 * a {@link DeckCollectionStore}; production uses {@link #defaultFile()} (a plain file under the
 * user's home directory — this game has no other persistence/account system to hang this off),
 * a test can inject its own store.
 */
public final class CollectionManager {
    private final DeckCollectionStore store;

    public CollectionManager() {
        this(new JsonDeckCollectionStore(defaultFile()));
    }

    public CollectionManager(DeckCollectionStore store) {
        if (store == null) throw new IllegalArgumentException("store required");
        this.store = store;
    }

    /** {@code ~/.elderpathcrusade/collection.json} — not inside assets/ (gitignored, build-relative) or any Gdx path. */
    public static File defaultFile() {
        return new File(System.getProperty("user.home"), ".elderpathcrusade/collection.json");
    }

    /**
     * Snapshots a won run's deck/Elders/relics into the collection, under {@code mode}
     * (currently always {@link GameMode#ROUGELIKE_RUN} — the only mode a run exists for, but the
     * field stays open to anything else "winning" might someday mean). Requires {@code
     * runState.isEnded() && runState.isWon()} — call only once {@code VictoryRoom} has reported
     * the outcome.
     */
    public DeckCollectionEntry recordWin(RunState runState, GameMode mode) {
        if (runState == null) throw new IllegalArgumentException("runState required");
        if (!runState.isEnded() || !runState.isWon()) {
            throw new IllegalStateException("recordWin requires an ended, won run");
        }
        DeckCollectionEntry entry = new DeckCollectionEntry(
                UUID.randomUUID().toString(),
                mode,
                runState.difficulty().level(),
                runState.durationMillis(),
                System.currentTimeMillis(),
                GameVersion.current(),
                runState.deck(),
                runState.elders(),
                runState.activeRelicIds());
        store.save(entry);
        return entry;
    }

    /** Every saved entry, oldest first — the browsing screen's data source. */
    public List<DeckCollectionEntry> all() {
        return store.loadAll();
    }
}
