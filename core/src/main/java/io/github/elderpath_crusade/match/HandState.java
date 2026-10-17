package io.github.elderpath_crusade.match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A player's hand: ordered (draw order) list of card instances. Pure state, no rendering. */
public class HandState {
    private final List<CardInstance> cards = new ArrayList<>();

    public List<CardInstance> getCards() {
        return Collections.unmodifiableList(cards);
    }

    public int size() {
        return cards.size();
    }

    public CardInstance find(String instanceId) {
        for (CardInstance card : cards) {
            if (card.instanceId().equals(instanceId)) return card;
        }
        return null;
    }

    public void add(CardInstance card) {
        if (card != null) cards.add(card);
    }

    /** Removes and returns the card with this instance id, or null if it isn't in this hand. */
    public CardInstance remove(String instanceId) {
        CardInstance card = find(instanceId);
        if (card != null) cards.remove(card);
        return card;
    }

    public void clear() {
        cards.clear();
    }
}
