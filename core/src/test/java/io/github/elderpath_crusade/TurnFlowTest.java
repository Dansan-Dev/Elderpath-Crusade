package io.github.elderpath_crusade;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.game_piece.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.game_piece.MonsterGamePiece;
import io.github.elderpath_crusade.managers.BoardManager;
import io.github.elderpath_crusade.managers.GameModeManager;
import io.github.elderpath_crusade.managers.PlayerManager;
import io.github.elderpath_crusade.managers.TurnManager;
import io.github.elderpath_crusade.multiplayer.EventBus;
import io.github.elderpath_crusade.multiplayer.GameEvent;
import io.github.elderpath_crusade.multiplayer.GameEventType;
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
        // Reset turn state
        TurnManager.reset();
        // Use a mocked board to avoid LibGDX dependencies
        board = mock(Board.class);
        BoardManager.setBoard(board);
        // Null out hands/decks so PlayerManager doesn't try to draw cards
        PlayerManager.setHand(PieceAlignment.P1, null);
        PlayerManager.setDeck(PieceAlignment.P1, null);
        PlayerManager.setHand(PieceAlignment.P2, null);
        PlayerManager.setDeck(PieceAlignment.P2, null);
    }

    @Test
    void startIfNeededStartsP1Turn() {
        List<GameEvent> captured = new ArrayList<>();
        Consumer<GameEvent> listener = captured::add;
        EventBus.register(GameEventType.TURN_STARTED, listener);

        try {
            TurnManager.startIfNeeded();

            assertEquals(PieceAlignment.P1, TurnManager.getCurrentPlayer());
            assertEquals(1, captured.size());
            assertEquals("P1", captured.get(0).getData().get("player"));
        } finally {
            EventBus.unregister(GameEventType.TURN_STARTED, listener);
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

        List<GameEvent> captured = new ArrayList<>();
        Consumer<GameEvent> listener = captured::add;
        EventBus.register(GameEventType.TURN_ENDED, listener);

        try {
            TurnManager.endTurn();

            assertEquals(1, captured.size());
            assertEquals("P1", captured.get(0).getData().get("player"));
        } finally {
            EventBus.unregister(GameEventType.TURN_ENDED, listener);
        }
    }

    @Test
    void turnStartResetsActionsForCurrentPlayer() {
        // Place a P1 piece on the board
        MonsterGamePiece p1Piece = new MonsterGamePiece(
            GamePieceStats.getMonsterStats(1, 5, 2, 2, 2),
            GamePieceType.MONSTER, PieceAlignment.P1, UUID.randomUUID(), null
        );
        p1Piece.getStats().setRemainingActions(0); // exhausted

        // Mock board.resetActionsForOwner to simulate the real behavior
        doAnswer(inv -> {
            PieceAlignment owner = inv.getArgument(0);
            if (owner == PieceAlignment.P1) {
                p1Piece.getStats().setRemainingActions(p1Piece.getEffectiveActions());
            }
            return null;
        }).when(board).resetActionsForOwner(any());

        TurnManager.startIfNeeded(); // starts P1 turn, which resets actions

        assertEquals(2, p1Piece.getStats().getRemainingActions());
    }

    @Test
    void turnStartEmitsActionsResetEvent() {
        List<GameEvent> captured = new ArrayList<>();
        Consumer<GameEvent> listener = captured::add;
        EventBus.register(GameEventType.ACTIONS_RESET, listener);

        try {
            TurnManager.startIfNeeded();

            assertEquals(1, captured.size());
            assertEquals("P1", captured.get(0).getData().get("player"));
        } finally {
            EventBus.unregister(GameEventType.ACTIONS_RESET, listener);
        }
    }

    @Test
    void turnStartEmitsManaChangedEvent() {
        List<GameEvent> captured = new ArrayList<>();
        Consumer<GameEvent> listener = captured::add;
        EventBus.register(GameEventType.MANA_CHANGED, listener);

        try {
            TurnManager.startIfNeeded();

            assertEquals(1, captured.size());
            assertEquals("P1", captured.get(0).getData().get("player"));
            assertEquals(1, captured.get(0).getData().get("mana")); // first turn = 1 mana
        } finally {
            EventBus.unregister(GameEventType.MANA_CHANGED, listener);
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
