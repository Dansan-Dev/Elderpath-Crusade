package io.github.elderpath_crusade.model.card;

import io.github.elderpath_crusade.model.piece.PieceStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DeckModelTest {

    private DeckModel deck;
    private CardModel wolf;
    private CardModel rogue;

    @BeforeEach
    void setUp() {
        wolf = new CardModel("Wolf", new PieceStats(1, 1, 1, 1, 1), List.of());
        rogue = new CardModel("Rogue", new PieceStats(2, 2, 2, 2, 1), List.of());
        deck = new DeckModel(new ArrayList<>(List.of(wolf, rogue)), new Random(42));
    }

    @Test
    void drawReturnsTopCard() {
        CardModel drawn = deck.draw();
        assertNotNull(drawn);
        assertEquals(1, deck.drawPileSize());
    }

    @Test
    void drawEmptyReturnsNull() {
        deck.draw();
        deck.draw();
        assertNull(deck.draw());
    }

    @Test
    void drawReshufflesDiscardWhenEmpty() {
        CardModel c1 = deck.draw();
        CardModel c2 = deck.draw();
        deck.discard(c1);
        deck.discard(c2);
        assertEquals(0, deck.drawPileSize());
        assertEquals(2, deck.discardPileSize());

        CardModel c3 = deck.draw();
        assertNotNull(c3);
        assertEquals(1, deck.drawPileSize());
        assertEquals(0, deck.discardPileSize());
    }

    @Test
    void discardAddsToDiscardPile() {
        deck.discard(wolf);
        assertEquals(1, deck.discardPileSize());
    }

    @Test
    void isEmpty() {
        assertFalse(deck.isEmpty());
        deck.draw();
        deck.draw();
        assertTrue(deck.isEmpty());
    }
}

class HandModelTest {

    private HandModel hand;
    private CardModel wolf;

    @BeforeEach
    void setUp() {
        hand = new HandModel(3);
        wolf = new CardModel("Wolf", new PieceStats(1, 1, 1, 1, 1), List.of());
    }

    @Test
    void addCard() {
        assertTrue(hand.addCard(wolf));
        assertEquals(1, hand.size());
    }

    @Test
    void addCardRejectsWhenFull() {
        hand.addCard(new CardModel("A", new PieceStats(1, 1, 1, 1, 1), List.of()));
        hand.addCard(new CardModel("B", new PieceStats(1, 1, 1, 1, 1), List.of()));
        hand.addCard(new CardModel("C", new PieceStats(1, 1, 1, 1, 1), List.of()));
        assertTrue(hand.isFull());
        assertFalse(hand.addCard(wolf));
    }

    @Test
    void removeCard() {
        hand.addCard(wolf);
        assertTrue(hand.removeCard(wolf));
        assertTrue(hand.isEmpty());
    }

    @Test
    void getCard() {
        hand.addCard(wolf);
        assertEquals(wolf, hand.getCard(0));
        assertNull(hand.getCard(5));
    }

    @Test
    void clear() {
        hand.addCard(wolf);
        hand.clear();
        assertTrue(hand.isEmpty());
    }
}
