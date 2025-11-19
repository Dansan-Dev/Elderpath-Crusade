package io.github.elderpath_crusade.managers;

import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;
import io.github.elderpath_crusade.enums.PieceAlignment;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Manages the drafted deck for the player.
 * Stores card creation functions that can be used to instantiate cards with the actual board reference.
 */
public final class DeckManager {
    private static List<Function<CardCreationParams, SummonCard>> draftedCardCreators = null;

    /**
     * Parameters needed to create a card instance.
     */
    public static class CardCreationParams {
        public final Board board;
        public final PieceAlignment alignment;
        public final int x, y, width, height, z;

        public CardCreationParams(Board board, PieceAlignment alignment, int x, int y, int width, int height, int z) {
            this.board = board;
            this.alignment = alignment;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.z = z;
        }
    }

    private DeckManager() {}

    /**
     * Store the drafted deck as card creation functions.
     */
    public static void setDraftedDeck(List<Function<CardCreationParams, SummonCard>> cardCreators) {
        draftedCardCreators = new ArrayList<>(cardCreators);
    }

    /**
     * Get the drafted deck as card creation functions.
     */
    public static List<Function<CardCreationParams, SummonCard>> getDraftedDeck() {
        return draftedCardCreators == null ? null : new ArrayList<>(draftedCardCreators);
    }

    /**
     * Check if a drafted deck exists.
     */
    public static boolean hasDraftedDeck() {
        return draftedCardCreators != null && !draftedCardCreators.isEmpty();
    }

    /**
     * Clear the drafted deck.
     */
    public static void clearDraftedDeck() {
        draftedCardCreators = null;
    }
}

