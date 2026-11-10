package io.github.elderpath_crusade.collection;

import java.util.List;

/** Where {@link DeckCollectionEntry}s are persisted — see {@link JsonDeckCollectionStore}. */
public interface DeckCollectionStore {
    /** Every saved entry, oldest first. Empty (not an error) if nothing has been saved yet. */
    List<DeckCollectionEntry> loadAll();

    /** Appends one entry and persists the whole collection. */
    void save(DeckCollectionEntry entry);
}
