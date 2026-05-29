package io.github.elderpath_crusade;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.events.*;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
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
        GameContext.get().getTurnManager().reset();
        board = mock(Board.class);
        GameContext.get().setActiveBoard(board);
        GameContext.get().getPlayerManager().setHand(PieceAlignment.P1, null);
        GameContext.get().getPlayerManager().setDeck(PieceAlignment.P1, null);
        GameContext.get().getPlayerManager().setHand(PieceAlignment.P2, null);
        GameContext.get().getPlayerManager().setDeck(PieceAlignment.P2, null);
    }

    @Test
    void startIfNeededStartsP1Turn() {
        List<TurnStartedEvent> captured = new ArrayList<>();
        Consumer<TurnStartedEvent> listener = captured::add;
        TypedEventBus.get().register(TurnStartedEvent.class, listener);

        try {
            GameContext.get().getTurnManager().startIfNeeded();

            assertEquals(PieceAlignment.P1, GameContext.get().getTurnManager().getCurrentPlayer());
            assertEquals(1, captured.size());
            assertEquals(PieceAlignment.P1, captured.get(0).player());
        } finally {
            TypedEventBus.get().unregister(TurnStartedEvent.class, listener);
        }
    }

    @Test
    void endTurnAlternatesPlayer() {
        GameContext.get().getTurnManager().startIfNeeded();
        assertEquals(PieceAlignment.P1, GameContext.get().getTurnManager().getCurrentPlayer());

        GameContext.get().getTurnManager().endTurn();
        assertEquals(PieceAlignment.P2, GameContext.get().getTurnManager().getCurrentPlayer());
    }

    @Test
    void endTurnEmitsTurnEndedEvent() {
        GameContext.get().getTurnManager().startIfNeeded();

        List<TurnEndedEvent> captured = new ArrayList<>();
        Consumer<TurnEndedEvent> listener = captured::add;
        TypedEventBus.get().register(TurnEndedEvent.class, listener);

        try {
            GameContext.get().getTurnManager().endTurn();

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

        GameContext.get().getTurnManager().startIfNeeded();

        assertEquals(2, p1Piece.getStats().getRemainingActions());
    }

    @Test
    void turnStartEmitsActionsResetEvent() {
        List<ActionsResetEvent> captured = new ArrayList<>();
        Consumer<ActionsResetEvent> listener = captured::add;
        TypedEventBus.get().register(ActionsResetEvent.class, listener);

        try {
            GameContext.get().getTurnManager().startIfNeeded();

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
            GameContext.get().getTurnManager().startIfNeeded();

            assertEquals(1, captured.size());
            assertEquals(PieceAlignment.P1, captured.get(0).player());
            assertEquals(1, captured.get(0).newMana()); // first turn = 1 mana
        } finally {
            TypedEventBus.get().unregister(ManaChangedEvent.class, listener);
        }
    }

    @Test
    void fullTurnCycleP1ToP2ToP1() {
        GameContext.get().getTurnManager().startIfNeeded();
        assertEquals(PieceAlignment.P1, GameContext.get().getTurnManager().getCurrentPlayer());

        GameContext.get().getTurnManager().endTurn();
        assertEquals(PieceAlignment.P2, GameContext.get().getTurnManager().getCurrentPlayer());

        GameContext.get().getTurnManager().endTurn();
        assertEquals(PieceAlignment.P1, GameContext.get().getTurnManager().getCurrentPlayer());
    }

    @Test
    void resetClearsStateForNewGame() {
        GameContext.get().getTurnManager().startIfNeeded();
        GameContext.get().getTurnManager().endTurn(); // now P2

        GameContext.get().getTurnManager().reset();
        assertEquals(PieceAlignment.P1, GameContext.get().getTurnManager().getCurrentPlayer());
        assertFalse(GameContext.get().getTurnManager().isWaitingForNextPlayer());
    }
}
