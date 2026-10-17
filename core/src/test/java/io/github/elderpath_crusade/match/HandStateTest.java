package io.github.elderpath_crusade.match;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HandStateTest {

    @Test
    void addKeepsDrawOrder() {
        HandState hand = new HandState();
        CardInstance a = new CardInstance("P1-0", "Wolf");
        CardInstance b = new CardInstance("P1-1", "Wolf");
        hand.add(a);
        hand.add(b);
        assertEquals(List.of(a, b), hand.getCards());
        assertEquals(2, hand.size());
    }

    @Test
    void remove_byInstanceId_removesThatCopyOnly() {
        HandState hand = new HandState();
        CardInstance a = new CardInstance("P1-0", "Wolf");
        CardInstance b = new CardInstance("P1-1", "Wolf");
        hand.add(a);
        hand.add(b);

        assertEquals(b, hand.remove("P1-1"));
        assertEquals(List.of(a), hand.getCards());
        assertNull(hand.remove("P1-1"));
    }

    @Test
    void find_unknownId_returnsNull() {
        assertNull(new HandState().find("nope"));
    }

    @Test
    void getCards_isReadOnly() {
        HandState hand = new HandState();
        assertThrows(UnsupportedOperationException.class, () -> hand.getCards().add(new CardInstance("x", "Wolf")));
    }

    @Test
    void cardInstance_requiresIdAndName() {
        assertThrows(IllegalArgumentException.class, () -> new CardInstance(" ", "Wolf"));
        assertThrows(IllegalArgumentException.class, () -> new CardInstance("P1-0", null));
    }
}
