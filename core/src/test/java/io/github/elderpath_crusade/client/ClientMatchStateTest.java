package io.github.elderpath_crusade.client;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.*;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.PickKind;
import io.github.elderpath_crusade.match.PickRequest;
import io.github.elderpath_crusade.net.protocol.MatchView;
import io.github.elderpath_crusade.net.protocol.MessageCodec;
import io.github.elderpath_crusade.net.protocol.ServerMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The replica from events alone (no StateSync), the way a UI sees each change as it happens. */
class ClientMatchStateTest {
    private ClientMatchState state;

    @BeforeEach
    void setUp() {
        state = new ClientMatchState();
        state.apply(new ServerMessage.Welcome(MessageCodec.PROTOCOL_VERSION, PieceAlignment.P1, 7, 5,
                List.of(new Coord(2, 1)), Map.of(PieceAlignment.P1, "HUMAN", PieceAlignment.P2, "HUMAN")));
    }

    private void event(GameEvent e) {
        state.apply(new ServerMessage.Event(e));
    }

    @Test
    void handsMirrorDrawsPlaysAndDiscards_ownByName_opponentByHandle() {
        event(new TurnStartedEvent(PieceAlignment.P1));
        event(new CardDrawnEvent(PieceAlignment.P1, "P1-0", "Wolf", 1));
        event(new CardDrawnEvent(PieceAlignment.P1, "P1-1", "Fireball", 2));
        event(new CardDrawnEvent(PieceAlignment.P2, "hidden-1", null, 1));
        event(new CardPlayedEvent("Wolf", PieceAlignment.P1, 0, 0, "w", "P1-0"));
        assertEquals(List.of("Fireball"), state.getOwnHand().stream().map(c -> c.cardName()).toList());
        assertEquals(1, state.getOpponentHandSize());
        event(new CardDiscardedEvent(PieceAlignment.P2, 1, List.of("hidden-1")));
        assertEquals(0, state.getOpponentHandSize());
        assertTrue(state.isStarted());
        assertEquals(PieceAlignment.P1, state.getCurrentPlayer());
    }

    @Test
    void piecesMirrorSpawnMoveDamageHealAndDeath_includingEffectSpawns() {
        event(new PieceSpawnedEvent("a", PieceAlignment.P1, 0, 0, "Runner"));
        state.apply(new ServerMessage.StateSync(List.of(
                new MatchView.PieceView("a", PieceAlignment.P1, "Runner", 0, 0, 3, 3, 1, 0, false))));
        event(new PieceSpawnedEvent("b", PieceAlignment.P2, 6, 4, "Runner")); // e.g. SummonPiece — no card play
        assertEquals("Runner", state.getPieceAt(6, 4).registryKey());

        event(new PieceMovedEvent("a", PieceAlignment.P1, 0, 0, 2, 0, PieceMovedEvent.MovementType.FORCED, "Push"));
        assertEquals("a", state.getPieceAt(2, 0).id());
        event(new PieceDamagedEvent("a", PieceAlignment.P1, 2, 0, 2));
        assertEquals(1, state.getPiece("a").currentHealth());
        event(new PieceHealedEvent("a", PieceAlignment.P1, 2, 0, 5));
        assertEquals(3, state.getPiece("a").currentHealth(), "healing caps at max health");
        event(new PieceAttackedEvent("b", PieceAlignment.P2, 3, 0, "a", 2, 0, 1));
        assertEquals(2, state.getPiece("a").currentHealth());
        event(new ActionSpentEvent("a", PieceAlignment.P1, 0));
        assertEquals(0, state.getPiece("a").remainingActions());
        event(new PieceDiedEvent("a", 2, 0));
        assertNull(state.getPiece("a"));
    }

    @Test
    void manaPicksRejectionsAndTheEnd() {
        event(new ManaChangedEvent(PieceAlignment.P2, 4));
        assertEquals(4, state.getMana(PieceAlignment.P2));

        PickRequest pick = new PickRequest(3, PieceAlignment.P1, PickKind.TILE, List.of(new Coord(0, 1)), "x", true);
        state.apply(new ServerMessage.PickRequested(pick));
        state.apply(new ServerMessage.PickAnswered(99));
        assertEquals(pick, state.getPendingPick(), "an answer to another pick leaves this one");
        state.apply(new ServerMessage.PickCancelled(3));
        assertNull(state.getPendingPick());

        state.apply(new ServerMessage.Rejected(4, io.github.elderpath_crusade.match.ActionResult.Reason.UNREACHABLE));
        assertEquals(4, state.getLastRejection().seq());

        state.apply(new ServerMessage.MatchEnded(PieceAlignment.P2));
        assertTrue(state.isEnded());
        assertEquals(PieceAlignment.P2, state.getWinner());
    }
}
