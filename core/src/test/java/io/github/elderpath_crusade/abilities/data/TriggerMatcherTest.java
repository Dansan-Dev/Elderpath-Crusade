package io.github.elderpath_crusade.abilities.data;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TriggerMatcherTest {

    @Test
    void spawned_mapsToOnSummon() {
        assertEquals(TriggerType.ON_SUMMON,
                TriggerMatcher.fromEvent(new PieceSpawnedEvent("p", PieceAlignment.P1, 0, 0)));
    }

    @Test
    void moved_mapsToOnMove() {
        assertEquals(TriggerType.ON_MOVE, TriggerMatcher.fromEvent(new PieceMovedEvent(
                "p", PieceAlignment.P1, 0, 0, 1, 0, PieceMovedEvent.MovementType.ACTIVE, "move")));
    }

    @Test
    void attacked_mapsToOnAttack() {
        assertEquals(TriggerType.ON_ATTACK, TriggerMatcher.fromEvent(new PieceAttackedEvent(
                "a", PieceAlignment.P1, 0, 0, "d", 1, 0, 1, List.of(), null)));
    }

    @Test
    void died_mapsToOnDeath() {
        assertEquals(TriggerType.ON_DEATH, TriggerMatcher.fromEvent(new PieceDiedEvent("p", 0, 0)));
    }

    @Test
    void killed_mapsToOnKill() {
        assertEquals(TriggerType.ON_KILL, TriggerMatcher.fromEvent(new PieceKilledEvent("k", "v", 0, 0, 0)));
    }

    @Test
    void turnStarted_mapsToOnTurnStart() {
        assertEquals(TriggerType.ON_TURN_START, TriggerMatcher.fromEvent(new TurnStartedEvent(PieceAlignment.P1)));
    }

    @Test
    void turnEnded_mapsToOnTurnEnd() {
        assertEquals(TriggerType.ON_TURN_END, TriggerMatcher.fromEvent(new TurnEndedEvent(PieceAlignment.P1)));
    }

    @Test
    void unmappedEvent_returnsNull() {
        assertNull(TriggerMatcher.fromEvent(new ManaChangedEvent(PieceAlignment.P1, 3)));
    }

    @Test
    void populateEventContext_movedSetsCoordinates() {
        ExpressionContext ctx = new ExpressionContext();
        TriggerMatcher.populateEventContext(new PieceMovedEvent(
                "p", PieceAlignment.P1, 0, 1, 2, 3, PieceMovedEvent.MovementType.ACTIVE, "move"), ctx);
        assertEquals("p", ctx.get("$event.pieceId"));
        assertEquals(0, ctx.get("$event.fromRow"));
        assertEquals(1, ctx.get("$event.fromCol"));
        assertEquals(2, ctx.get("$event.toRow"));
        assertEquals(3, ctx.get("$event.toCol"));
    }

    @Test
    void populateEventContext_turnStartedSetsPlayerName() {
        ExpressionContext ctx = new ExpressionContext();
        TriggerMatcher.populateEventContext(new TurnStartedEvent(PieceAlignment.P2), ctx);
        assertEquals("P2", ctx.get("$event.player"));
    }

    @Test
    void populateEventContext_unmappedEventSetsNothing() {
        ExpressionContext ctx = new ExpressionContext();
        TriggerMatcher.populateEventContext(new ManaChangedEvent(PieceAlignment.P1, 3), ctx);
        assertNull(ctx.get("$event.player"));
    }
}
