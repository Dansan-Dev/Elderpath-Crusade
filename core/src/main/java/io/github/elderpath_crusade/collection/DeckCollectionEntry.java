package io.github.elderpath_crusade.collection;

import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.run.RunCardInstance;

import java.util.List;

/**
 * One winning roguelike run, snapshotted into the player's persistent deck collection
 * (Sprint 64) — the deck as it stood at the win (each card's own modifications, UPG-003), the
 * cards retired to Elder status (ELDER-001), the relics that were active, and metadata about
 * the run itself. {@link CollectionManager#recordWin} builds one of these from an ended, won
 * {@code RunState}; {@link DeckCollectionStore} persists it.
 *
 * <p>Jackson-serializable as-is (a plain record, same as every {@code net/protocol} message —
 * see {@code MessageCodec}) — no custom (de)serializer needed.
 */
public record DeckCollectionEntry(
        String id,
        GameMode mode,
        int difficultyLevel,
        long runCompletionTimeMillis,
        long timestampMillis,
        String gameVersion,
        List<RunCardInstance> deck,
        List<RunCardInstance> elders,
        List<String> relicIds
) {
    public DeckCollectionEntry {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id required");
        if (mode == null) throw new IllegalArgumentException("mode required");
        if (gameVersion == null) throw new IllegalArgumentException("gameVersion required");
        deck = List.copyOf(deck);
        elders = List.copyOf(elders);
        relicIds = List.copyOf(relicIds);
    }
}
