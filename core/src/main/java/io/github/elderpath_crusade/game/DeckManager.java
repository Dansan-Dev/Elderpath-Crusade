package io.github.elderpath_crusade.game;

import io.github.elderpath_crusade.enums.PieceAlignment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds each player's drafted deck as card names (CardCatalog display names), carried from
 * the draft into the match, where they become that player's DeckState.
 */
public final class DeckManager {
    private final Map<PieceAlignment, List<String>> draftedDecks = new HashMap<>();

    /** Layout parameters for building a renderable card view (CardFactory.create). */
    public record CardCreationParams(
        PieceAlignment alignment,
        int x,
        int y,
        int width,
        int height,
        int z
    ) {}

    public DeckManager() {}

    public void setDraftedDeck(PieceAlignment player, List<String> cardNames) {
        draftedDecks.put(player, new ArrayList<>(cardNames));
    }

    public List<String> getDraftedDeck(PieceAlignment player) {
        List<String> deck = draftedDecks.get(player);
        return deck == null ? new ArrayList<>() : new ArrayList<>(deck);
    }

    public boolean hasDraftedDeck(PieceAlignment player) {
        List<String> deck = draftedDecks.get(player);
        return deck != null && !deck.isEmpty();
    }

    public void clearDraftedDecks() {
        draftedDecks.clear();
    }
}
