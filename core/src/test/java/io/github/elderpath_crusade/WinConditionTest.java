package io.github.elderpath_crusade;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.*;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.managers.BoardManager;
import io.github.elderpath_crusade.managers.WinConditionManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WinConditionTest {

    private Board board;
    private GameWonEvent capturedEvent;

    @BeforeAll
    static void initOnce() {
        Gdx.app = mock(Application.class);
        TypedEventBus.get().clear();
        GameContext.create();
        WinConditionManager.initialize();
    }

    @BeforeEach
    void setUp() {
        WinConditionManager.reset();
        capturedEvent = null;
        TypedEventBus.get().register(GameWonEvent.class, e -> capturedEvent = e);
        board = mock(Board.class);
        when(board.getROWS()).thenReturn(5);
        when(board.isFlipped()).thenReturn(false);
        BoardManager.setBoard(board);
    }

    @Test
    void p1WinsWhenPieceMovesToLastRow() {
        TypedEventBus.get().emit(new PieceMovedEvent(
                "p1", PieceAlignment.P1, 3, 0, 4, 0,
                PieceMovedEvent.MovementType.ACTIVE, "move"));

        assertNotNull(capturedEvent);
        assertEquals(PieceAlignment.P1, capturedEvent.winner());
    }

    @Test
    void p2WinsWhenPieceMovesToRow0() {
        TypedEventBus.get().emit(new PieceMovedEvent(
                "p2", PieceAlignment.P2, 1, 0, 0, 0,
                PieceMovedEvent.MovementType.ACTIVE, "move"));

        assertNotNull(capturedEvent);
        assertEquals(PieceAlignment.P2, capturedEvent.winner());
    }

    @Test
    void noWinWhenPieceMovesToMiddleRow() {
        TypedEventBus.get().emit(new PieceMovedEvent(
                "p1", PieceAlignment.P1, 1, 0, 2, 0,
                PieceMovedEvent.MovementType.ACTIVE, "move"));

        assertNull(capturedEvent);
    }

    @Test
    void neutralPieceDoesNotTriggerWin() {
        TypedEventBus.get().emit(new PieceMovedEvent(
                "n1", PieceAlignment.NEUTRAL, 3, 0, 4, 0,
                PieceMovedEvent.MovementType.ACTIVE, "move"));

        assertNull(capturedEvent);
    }

    @Test
    void p1WinsOnSpawnAtLastRow() {
        TypedEventBus.get().emit(new PieceSpawnedEvent("p1", PieceAlignment.P1, 4, 0));

        assertNotNull(capturedEvent);
        assertEquals(PieceAlignment.P1, capturedEvent.winner());
    }

    @Test
    void winOnlyTriggersOnce() {
        TypedEventBus.get().emit(new PieceMovedEvent(
                "p1", PieceAlignment.P1, 3, 0, 4, 0,
                PieceMovedEvent.MovementType.ACTIVE, "move"));
        assertNotNull(capturedEvent);

        // Reset captured event to detect if second event would trigger again
        capturedEvent = null;

        // Second event should NOT trigger win again (gameWon is already true)
        TypedEventBus.get().emit(new PieceMovedEvent(
                "p2", PieceAlignment.P2, 1, 0, 0, 0,
                PieceMovedEvent.MovementType.ACTIVE, "move"));
        assertNull(capturedEvent);
    }
}
