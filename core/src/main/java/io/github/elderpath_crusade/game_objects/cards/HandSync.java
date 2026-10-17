package io.github.elderpath_crusade.game_objects.cards;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.CardDiscardedEvent;
import io.github.elderpath_crusade.events.CardDrawnEvent;
import io.github.elderpath_crusade.events.CardPlayedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game.DeckManager;
import io.github.elderpath_crusade.match.CardInstance;
import io.github.elderpath_crusade.utils.Logger;

/**
 * Keeps a renderable Hand in step with its owner's hand state by mirroring the card events
 * (drawn → add a card view, played/discarded → remove it), so the same view code works
 * whether the events come from the in-process simulation (bindToEventBus) or are relayed
 * from a remote server (the caller invokes the on* methods directly).
 */
public class HandSync {
    private final Hand hand;

    public HandSync(Hand hand) {
        this.hand = hand;
    }

    /** Mirrors the local simulation's card events for the current session. */
    public void bindToEventBus() {
        TypedEventBus bus = TypedEventBus.get();
        bus.registerScoped("session", CardDrawnEvent.class, this::onCardDrawn);
        bus.registerScoped("session", CardPlayedEvent.class, this::onCardPlayed);
        bus.registerScoped("session", CardDiscardedEvent.class, this::onCardDiscarded);
    }

    public void onCardDrawn(CardDrawnEvent e) {
        if (e.owner() == owner()) addCard(new CardInstance(e.instanceId(), e.cardName()));
    }

    public void onCardPlayed(CardPlayedEvent e) {
        if (e.owner() == owner()) hand.removeByInstanceId(e.instanceId());
    }

    public void onCardDiscarded(CardDiscardedEvent e) {
        if (e.player() != owner() || e.instanceIds() == null) return;
        for (String instanceId : e.instanceIds()) hand.removeByInstanceId(instanceId);
    }

    /** Adds a card view for this match card at the end of the hand. */
    public Card addCard(CardInstance card) {
        try {
            Card view = CardFactory.create(card.cardName(), card.instanceId(), new DeckManager.CardCreationParams(
                    owner(), 0, 0, hand.getCardWidth(), hand.getCardHeight(), 0));
            hand.addCard(view);
            return view;
        } catch (IllegalArgumentException ex) {
            Logger.error("HandSync", "Unknown card: " + card.cardName());
            return null;
        }
    }

    public void clear() {
        for (Card card : new java.util.ArrayList<>(hand.getCards())) hand.removeCard(card);
    }

    private PieceAlignment owner() {
        return hand.getOwner();
    }
}
