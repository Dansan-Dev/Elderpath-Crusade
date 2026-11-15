package io.github.elderpath_crusade.server;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.CardDiscardedEvent;
import io.github.elderpath_crusade.events.CardDrawnEvent;
import io.github.elderpath_crusade.events.CardPlayedEvent;
import io.github.elderpath_crusade.events.GameEvent;
import io.github.elderpath_crusade.events.ManaChangedEvent;
import io.github.elderpath_crusade.match.CardInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HiddenInfoFilterTest {
    private HiddenInfoFilter filter;

    @BeforeEach
    void setUp() {
        filter = new HiddenInfoFilter(PieceAlignment.P1);
    }

    private CardDrawnEvent opponentDraw(String id) {
        return (CardDrawnEvent) filter.filter(new CardDrawnEvent(PieceAlignment.P2, id, "Wolf", 3));
    }

    @Test
    void opponentDraw_isHiddenHandleWithNullName() {
        CardDrawnEvent out = opponentDraw("P2-1");
        assertTrue(out.instanceId().startsWith("hidden-"));
        assertNotEquals("P2-1", out.instanceId());
        assertNull(out.cardName());
        assertEquals(PieceAlignment.P2, out.owner());
        assertEquals(3, out.handSize());
    }

    @Test
    void ownDraw_isUnchanged() {
        CardDrawnEvent in = new CardDrawnEvent(PieceAlignment.P1, "P1-1", "Wolf", 2);
        assertSame(in, filter.filter(in));
    }

    @Test
    void opponentPlay_keepsNameButUsesHandle() {
        String handle = opponentDraw("P2-1").instanceId();
        CardPlayedEvent out = (CardPlayedEvent) filter.filter(
                new CardPlayedEvent("Wolf", PieceAlignment.P2, 1, 2, "piece-1", "P2-1"));
        assertEquals("Wolf", out.cardName());
        assertEquals(handle, out.instanceId());
        assertNotEquals("P2-1", out.instanceId());
        assertEquals(1, out.row());
        assertEquals(2, out.col());
        assertEquals("piece-1", out.pieceId());
    }

    @Test
    void ownPlay_isUnchanged() {
        CardPlayedEvent in = new CardPlayedEvent("Wolf", PieceAlignment.P1, 1, 2, "piece-1", "P1-1");
        assertSame(in, filter.filter(in));
    }

    @Test
    void cardDrawnAgainAfterPlay_getsNewHandle() {
        String first = opponentDraw("P2-1").instanceId();
        filter.filter(new CardPlayedEvent("Wolf", PieceAlignment.P2, 1, 2, "piece-1", "P2-1"));
        String second = opponentDraw("P2-1").instanceId();
        assertNotEquals(first, second);
    }

    @Test
    void cardDrawnAgainAfterDiscard_getsNewHandle() {
        String first = opponentDraw("P2-1").instanceId();
        filter.filter(new CardDiscardedEvent(PieceAlignment.P2, 1, List.of("P2-1")));
        assertNotEquals(first, opponentDraw("P2-1").instanceId());
    }

    @Test
    void sameCardDrawnTwiceWithoutRelease_keepsHandle() {
        assertEquals(opponentDraw("P2-1").instanceId(), opponentDraw("P2-1").instanceId());
    }

    @Test
    void opponentDiscard_replacesIdsWithHandles() {
        String h1 = opponentDraw("P2-1").instanceId();
        String h2 = opponentDraw("P2-2").instanceId();
        CardDiscardedEvent out = (CardDiscardedEvent) filter.filter(
                new CardDiscardedEvent(PieceAlignment.P2, 2, List.of("P2-1", "P2-2")));
        assertEquals(List.of(h1, h2), out.instanceIds());
        assertEquals(2, out.count());
    }

    @Test
    void ownDiscard_isUnchanged() {
        CardDiscardedEvent in = new CardDiscardedEvent(PieceAlignment.P1, 1, List.of("P1-1"));
        assertSame(in, filter.filter(in));
    }

    @Test
    void opponentHand_returnsSameHandlesOnRepeatedCalls() {
        List<CardInstance> hand = List.of(new CardInstance("P2-1", "Wolf"), new CardInstance("P2-2", "Cub"));
        List<String> first = filter.opponentHand(hand);
        List<String> second = filter.opponentHand(hand);
        assertEquals(2, first.size());
        assertEquals(first, second);
        assertNotEquals(first.get(0), first.get(1));
        first.forEach(h -> assertTrue(h.startsWith("hidden-")));
    }

    @Test
    void opponentHand_matchesHandleFromDraw() {
        String drawn = opponentDraw("P2-1").instanceId();
        assertEquals(List.of(drawn), filter.opponentHand(List.of(new CardInstance("P2-1", "Wolf"))));
    }

    @Test
    void releasingUnseenId_stillYieldsHandle() {
        CardPlayedEvent out = (CardPlayedEvent) filter.filter(
                new CardPlayedEvent("Wolf", PieceAlignment.P2, 0, 0, "p", "P2-99"));
        assertTrue(out.instanceId().startsWith("hidden-"));
        assertNotEquals("P2-99", out.instanceId());
    }

    @Test
    void unrelatedEvents_passThrough() {
        GameEvent in = new ManaChangedEvent(PieceAlignment.P2, 4);
        assertSame(in, filter.filter(in));
    }
}
