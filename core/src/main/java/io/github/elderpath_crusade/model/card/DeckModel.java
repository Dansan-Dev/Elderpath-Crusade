package io.github.elderpath_crusade.model.card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Pure model for a deck. Draw pile + discard pile, shuffle logic.
 */
public class DeckModel {
    private final List<CardModel> drawPile = new ArrayList<>();
    private final List<CardModel> discardPile = new ArrayList<>();
    private final Random rng;

    public DeckModel(List<CardModel> cards) {
        this(cards, new Random());
    }

    public DeckModel(List<CardModel> cards, Random rng) {
        this.rng = rng;
        if (cards != null) drawPile.addAll(cards);
    }

    public int drawPileSize() { return drawPile.size(); }
    public int discardPileSize() { return discardPile.size(); }
    public boolean isEmpty() { return drawPile.isEmpty() && discardPile.isEmpty(); }

    /**
     * Draw the top card. If draw pile is empty, shuffles discard into draw pile first.
     * Returns null if both piles are empty.
     */
    public CardModel draw() {
        if (drawPile.isEmpty()) {
            if (discardPile.isEmpty()) return null;
            shuffle();
        }
        return drawPile.remove(0);
    }

    public void discard(CardModel card) {
        if (card != null) discardPile.add(card);
    }

    public void shuffle() {
        drawPile.addAll(discardPile);
        discardPile.clear();
        Collections.shuffle(drawPile, rng);
    }

    public void addCards(List<CardModel> cards) {
        if (cards != null) drawPile.addAll(cards);
    }

    public List<CardModel> getDrawPile() { return Collections.unmodifiableList(drawPile); }
    public List<CardModel> getDiscardPile() { return Collections.unmodifiableList(discardPile); }
}
