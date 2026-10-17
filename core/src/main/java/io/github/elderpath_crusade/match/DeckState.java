package io.github.elderpath_crusade.match;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.CardDiscardedEvent;
import io.github.elderpath_crusade.events.CardDrawnEvent;
import io.github.elderpath_crusade.events.CardShuffledEvent;
import io.github.elderpath_crusade.events.TypedEventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A player's deck: draw pile + discard pile, moving cards into and out of that player's
 * HandState and emitting the card events (CardDrawnEvent, CardShuffledEvent,
 * CardDiscardedEvent) that hand views and network replicas mirror. Pure state, no rendering.
 *
 * Instance ids are "&lt;owner&gt;-&lt;n&gt;" in the order the deck was built, so a seeded
 * Random makes a whole match's card flow deterministic.
 */
public class DeckState {
    private final PieceAlignment owner;
    private final HandState hand;
    private final Random rng;
    private final List<CardInstance> drawPile = new ArrayList<>();
    private final List<CardInstance> discardPile = new ArrayList<>();

    public DeckState(PieceAlignment owner, HandState hand, List<String> cardNames, Random rng) {
        if (owner == null || hand == null || rng == null) {
            throw new IllegalArgumentException("owner, hand and rng are required");
        }
        this.owner = owner;
        this.hand = hand;
        this.rng = rng;
        if (cardNames != null) {
            for (String name : cardNames) {
                drawPile.add(new CardInstance(owner.name() + "-" + drawPile.size(), name));
            }
        }
    }

    public PieceAlignment getOwner() { return owner; }

    public List<CardInstance> getDrawPile() { return Collections.unmodifiableList(drawPile); }

    public List<CardInstance> getDiscardPile() { return Collections.unmodifiableList(discardPile); }

    /**
     * Draws the top card into the hand, reshuffling the discard pile into the draw pile first
     * if the draw pile is empty. Returns the drawn card, or null if both piles are empty.
     */
    public CardInstance draw() {
        if (drawPile.isEmpty()) {
            if (discardPile.isEmpty()) return null;
            shuffle();
        }
        CardInstance card = drawPile.remove(0);
        hand.add(card);
        TypedEventBus.get().emit(new CardDrawnEvent(owner, card.instanceId(), card.cardName(), hand.size()));
        return card;
    }

    /** Moves the discard pile back into the draw pile and shuffles it. */
    public void shuffle() {
        if (!discardPile.isEmpty()) {
            drawPile.addAll(discardPile);
            discardPile.clear();
        }
        if (!drawPile.isEmpty()) {
            Collections.shuffle(drawPile, rng);
            TypedEventBus.get().emit(new CardShuffledEvent(owner, drawPile.size()));
        }
    }

    /**
     * Moves a played card from the hand to the discard pile. Emits nothing — the caller
     * (CardRules) emits CardPlayedEvent, which tells views the card left the hand.
     */
    public boolean playFromHand(String instanceId) {
        CardInstance card = hand.remove(instanceId);
        if (card == null) return false;
        discardPile.add(card);
        return true;
    }

    /** Discards one card from the hand without playing it (e.g. a DiscardCard effect). */
    public boolean discardFromHand(String instanceId) {
        CardInstance card = hand.remove(instanceId);
        if (card == null) return false;
        discardPile.add(card);
        TypedEventBus.get().emit(new CardDiscardedEvent(owner, 1, List.of(card.instanceId())));
        return true;
    }

    /** Discards the whole hand (end of turn). Always emits CardDiscardedEvent, even for an empty hand. */
    public void discardHand() {
        List<String> ids = new ArrayList<>();
        for (CardInstance card : hand.getCards()) {
            ids.add(card.instanceId());
            discardPile.add(card);
        }
        hand.clear();
        TypedEventBus.get().emit(new CardDiscardedEvent(owner, ids.size(), ids));
    }
}
