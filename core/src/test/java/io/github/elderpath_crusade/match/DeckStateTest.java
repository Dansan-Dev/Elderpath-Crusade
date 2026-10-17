package io.github.elderpath_crusade.match;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.CardDiscardedEvent;
import io.github.elderpath_crusade.events.CardDrawnEvent;
import io.github.elderpath_crusade.events.CardShuffledEvent;
import io.github.elderpath_crusade.events.GameEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DeckStateTest {
    private final List<GameEvent> events = new ArrayList<>();
    private HandState hand;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        TypedEventBus.get().register(CardDrawnEvent.class, events::add);
        TypedEventBus.get().register(CardShuffledEvent.class, events::add);
        TypedEventBus.get().register(CardDiscardedEvent.class, events::add);
        hand = new HandState();
    }

    private DeckState deck(long seed, String... names) {
        return new DeckState(PieceAlignment.P1, hand, List.of(names), new Random(seed));
    }

    @Test
    void constructor_assignsSequentialInstanceIdsInDeckOrder() {
        DeckState deck = deck(1, "Wolf", "Wolf", "Fireball");
        assertEquals(List.of("P1-0", "P1-1", "P1-2"),
                deck.getDrawPile().stream().map(CardInstance::instanceId).toList());
        assertEquals("Fireball", deck.getDrawPile().get(2).cardName());
    }

    @Test
    void draw_movesTopCardToHandAndEmitsCardDrawnEvent() {
        DeckState deck = deck(1, "Wolf", "Rogue");

        CardInstance drawn = deck.draw();

        assertEquals(new CardInstance("P1-0", "Wolf"), drawn);
        assertEquals(List.of(drawn), hand.getCards());
        assertEquals(1, deck.getDrawPile().size());
        assertEquals(List.of(new CardDrawnEvent(PieceAlignment.P1, "P1-0", "Wolf", 1)), events);
    }

    @Test
    void draw_withBothPilesEmpty_returnsNullAndEmitsNothing() {
        DeckState deck = deck(1);
        assertNull(deck.draw());
        assertTrue(events.isEmpty());
    }

    @Test
    void draw_withEmptyDrawPile_reshufflesDiscardPileFirst() {
        DeckState deck = deck(1, "Wolf");
        CardInstance first = deck.draw();
        deck.discardHand();
        events.clear();

        CardInstance again = deck.draw();

        assertEquals(first, again);
        assertTrue(deck.getDiscardPile().isEmpty());
        assertInstanceOf(CardShuffledEvent.class, events.get(0));
        assertInstanceOf(CardDrawnEvent.class, events.get(1));
    }

    @Test
    void shuffle_sameSeedGivesSameOrder() {
        String[] names = {"A", "B", "C", "D", "E", "F", "G", "H"};
        DeckState a = deck(42, names);
        DeckState b = new DeckState(PieceAlignment.P1, new HandState(), List.of(names), new Random(42));

        a.shuffle();
        b.shuffle();

        assertEquals(a.getDrawPile(), b.getDrawPile());
    }

    @Test
    void shuffle_emptyDeck_emitsNothing() {
        deck(1).shuffle();
        assertTrue(events.isEmpty());
    }

    @Test
    void playFromHand_movesCardToDiscardWithoutEvent() {
        DeckState deck = deck(1, "Wolf", "Rogue");
        deck.draw();
        CardInstance rogue = deck.draw();
        events.clear();

        assertTrue(deck.playFromHand(rogue.instanceId()));

        assertEquals(1, hand.size());
        assertEquals(List.of(rogue), deck.getDiscardPile());
        assertTrue(events.isEmpty());
        assertFalse(deck.playFromHand(rogue.instanceId()), "already played");
    }

    @Test
    void discardFromHand_emitsDiscardOfThatInstanceOnly() {
        DeckState deck = deck(1, "Wolf", "Rogue");
        CardInstance wolf = deck.draw();
        deck.draw();
        events.clear();

        assertTrue(deck.discardFromHand(wolf.instanceId()));

        assertEquals(List.of(new CardDiscardedEvent(PieceAlignment.P1, 1, List.of("P1-0"))), events);
        assertEquals(1, hand.size());
    }

    @Test
    void discardHand_emptiesHandIntoDiscardAndListsIds() {
        DeckState deck = deck(1, "Wolf", "Rogue", "Fairy");
        deck.draw();
        deck.draw();
        events.clear();

        deck.discardHand();

        assertEquals(0, hand.size());
        assertEquals(2, deck.getDiscardPile().size());
        assertEquals(List.of(new CardDiscardedEvent(PieceAlignment.P1, 2, List.of("P1-0", "P1-1"))), events);
    }

    @Test
    void discardHand_emptyHandStillEmitsEvent() {
        deck(1, "Wolf").discardHand();
        assertEquals(List.of(new CardDiscardedEvent(PieceAlignment.P1, 0, List.of())), events);
    }
}
