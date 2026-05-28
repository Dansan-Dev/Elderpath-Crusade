package io.github.elderpath_crusade;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.events.*;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.managers.BoardManager;
import io.github.elderpath_crusade.managers.PlayerManager;
import io.github.elderpath_crusade.managers.TurnManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TurnFlowTest {

    private Board board;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        TurnManager.reset();
        board = mock(Board.class);
        BoardManager.setBoard(board);
        PlayerManager.setHand(PieceAlignment.P1, null);
        PlayerManager.setDeck(PieceAlignment.P1, null);
        PlayerManager.setHand(PieceAlignment.P2, null);
        PlayerManager.setDeck(PieceAlignment.P2, null);
    }

    @Test
    void startIfNeededStartsP1Turn() {
        List<TurnStartedEvent> captured = new ArrayList<>();
        Consumer<TurnStartedEvent> listener = captured::add;
        TypedEventBus.get().register(TurnStartedEvent.class, listener);

        try {
            TurnManager.startIfNeeded();

            assertEquals(PieceAlignment.P1, TurnManager.getCurrentPlayer());
            assertEquals(1, captured.size());
            assertEquals(PieceAlignment.P1, captured.get(0).player());
        } finally {
            TypedEventBus.get().unregister(TurnStartedEvent.class, listener);
        }
    }

    @Test
    void endTurnAlternatesPlayer() {
        TurnManager.startIfNeeded();
        assertEquals(PieceAlignment.P1, TurnManager.getCurrentPlayer());

        TurnManager.endTurn();
        assertEquals(PieceAlignment.P2, TurnManager.getCurrentPlayer());
    }

    @Test
    void endTurnEmitsTurnEndedEvent() {
        TurnManager.startIfNeeded();

        List<TurnEndedEvent> captured = new ArrayList<>();
        Consumer<TurnEndedEvent> listener = captured::add;
        TypedEventBus.get().register(TurnEndedEvent.class, listener);

        try {
            TurnManager.endTurn();

            assertEquals(1, captured.size());
            assertEquals(PieceAlignment.P1, captured.get(0).player());
        } finally {
            TypedEventBus.get().unregister(TurnEndedEvent.class, listener);
        }
    }

    @Test
    void turnStartResetsActionsForCurrentPlayer() {
        MonsterGamePiece p1Piece = new MonsterGamePiece(
            GamePieceStats.getMonsterStats(1, 5, 2, 2, 2),
            GamePieceType.MONSTER, PieceAlignment.P1, UUID.randomUUID(), null
        );
        p1Piece.getStats().setRemainingActions(0);

        doAnswer(inv -> {
            PieceAlignment owner = inv.getArgument(0);
            if (owner == PieceAlignment.P1) {
                p1Piece.getStats().setRemainingActions(p1Piece.getEffectiveActions());
            }
            return null;
        }).when(board).resetActionsForOwner(any());

        TurnManager.startIfNeeded();

        assertEquals(2, p1Piece.getStats().getRemainingActions());
    }

    @Test
    void turnStartEmitsActionsResetEvent() {
        List<ActionsResetEvent> captured = new ArrayList<>();
        Consumer<ActionsResetEvent> listener = captured::add;
        TypedEventBus.get().register(ActionsResetEvent.class, listener);

        try {
            TurnManager.startIfNeeded();

            assertEquals(1, captured.size());
            assertEquals(PieceAlignment.P1, captured.get(0).player());
        } finally {
            TypedEventBus.get().unregister(ActionsResetEvent.class, listener);
        }
    }

    @Test
    void turnStartEmitsManaChangedEvent() {
        List<ManaChangedEvent> captured = new ArrayList<>();
        Consumer<ManaChangedEvent> listener = captured::add;
        TypedEventBus.get().register(ManaChangedEvent.class, listener);

        try {
            TurnManager.startIfNeeded();

            assertEquals(1, captured.size());
            assertEquals(PieceAlignment.P1, captured.get(0).player());
            assertEquals(1, captured.get(0).newMana()); // first turn = 1 mana
        } finally {
            TypedEventBus.get().unregister(ManaChangedEvent.class, listener);
        }
    }

    @Test
    void fullTurnCycleP1ToP2ToP1() {
        TurnManager.startIfNeeded();
        assertEquals(PieceAlignment.P1, TurnManager.getCurrentPlayer());

        TurnManager.endTurn();
        assertEquals(PieceAlignment.P2, TurnManager.getCurrentPlayer());

        TurnManager.endTurn();
        assertEquals(PieceAlignment.P1, TurnManager.getCurrentPlayer());
    }

    @Test
    void resetClearsStateForNewGame() {
        TurnManager.startIfNeeded();
        TurnManager.endTurn(); // now P2

        TurnManager.reset();
        assertEquals(PieceAlignment.P1, TurnManager.getCurrentPlayer());
        assertFalse(TurnManager.isWaitingForNextPlayer());
    }
}
