package io.github.elderpath_crusade.model.card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure model for a hand of cards.
 */
public class HandModel {
    private final List<CardModel> cards = new ArrayList<>();
    private final int maxSize;

    public HandModel(int maxSize) {
        this.maxSize = maxSize;
    }

    public List<CardModel> getCards() { return Collections.unmodifiableList(cards); }
    public int size() { return cards.size(); }
    public boolean isFull() { return cards.size() >= maxSize; }
    public boolean isEmpty() { return cards.isEmpty(); }
    public int getMaxSize() { return maxSize; }

    public boolean addCard(CardModel card) {
        if (card == null || isFull()) return false;
        cards.add(card);
        return true;
    }

    public boolean removeCard(CardModel card) {
        return cards.remove(card);
    }

    public CardModel getCard(int index) {
        if (index < 0 || index >= cards.size()) return null;
        return cards.get(index);
    }

    public void clear() { cards.clear(); }
}
