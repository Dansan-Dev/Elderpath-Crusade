package io.github.elderpath_crusade.game;

import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.cards.Card;
import io.github.elderpath_crusade.enums.PieceAlignment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Manages the drafted decks for players.
 * Stores card creation functions that can be used to instantiate cards with the actual board reference.
 * Supports both single-player (P1) and multi-player (P1 and P2) modes.
 */
public final class DeckManager {
    private List<Function<CardCreationParams, Card>> draftedCardCreators = null; // Legacy: P1 deck
    private Map<PieceAlignment, List<Function<CardCreationParams, Card>>> draftedDecks = new HashMap<>();

    /**
     * Parameters needed to create a card instance.
     */
    public record CardCreationParams(
        Board board,
        PieceAlignment alignment,
        int x,
        int y,
        int width,
        int height,
        int z
    ) {}

    public DeckManager() {}

    /**
     * Store the drafted deck as card creation functions for a specific player.
     */
    public void setDraftedDeck(PieceAlignment player, List<Function<CardCreationParams, Card>> cardCreators) {
        draftedDecks.put(player, new ArrayList<>(cardCreators));
    }

    /**
     * Get the drafted deck as card creation functions for a specific player.
     */
    public List<Function<CardCreationParams, Card>> getDraftedDeck(PieceAlignment player) {
        List<Function<CardCreationParams, Card>> deck = draftedDecks.get(player);
        return deck == null ? new ArrayList<>() : new ArrayList<>(deck);
    }

    /**
     * Check if a drafted deck exists for a specific player.
     */
    public boolean hasDraftedDeck(PieceAlignment player) {
        List<Function<CardCreationParams, Card>> deck = draftedDecks.get(player);
        return deck != null && !deck.isEmpty();
    }

    /**
     * Clear all drafted decks.
     */
    public void clearDraftedDecks() {
        draftedDecks.clear();
        draftedCardCreators = null;
    }

    // Legacy methods for backward compatibility (use P1 by default)
    /**
     * Store the drafted deck as card creation functions (legacy - stores for P1).
     */
    public void setDraftedDeck(List<Function<CardCreationParams, Card>> cardCreators) {
        draftedCardCreators = new ArrayList<>(cardCreators);
        setDraftedDeck(PieceAlignment.P1, cardCreators);
    }

    /**
     * Get the drafted deck as card creation functions (legacy - returns P1 deck).
     */
    public List<Function<CardCreationParams, Card>> getDraftedDeck() {
        if (draftedCardCreators != null) {
            return new ArrayList<>(draftedCardCreators);
        }
        return getDraftedDeck(PieceAlignment.P1);
    }

    /**
     * Check if a drafted deck exists (legacy - checks P1 deck).
     */
    public boolean hasDraftedDeck() {
        if (draftedCardCreators != null && !draftedCardCreators.isEmpty()) {
            return true;
        }
        return hasDraftedDeck(PieceAlignment.P1);
    }

    /**
     * Clear the drafted deck (legacy - clears P1 deck).
     */
    public void clearDraftedDeck() {
        draftedCardCreators = null;
        draftedDecks.remove(PieceAlignment.P1);
    }
}
