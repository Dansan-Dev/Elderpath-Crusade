package io.github.elderpath_crusade;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.*;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.managers.BoardManager;
import io.github.elderpath_crusade.managers.GameManager;
import io.github.elderpath_crusade.managers.WinConditionManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WinConditionTest {

    private Board board;

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
        GameManager.unlockInteractions();
        board = mock(Board.class);
        when(board.getROWS()).thenReturn(5);
        when(board.isFlipped()).thenReturn(false);
        BoardManager.setBoard(board);
    }

    private void emitIgnoringTimer(GameEvent event) {
        try {
            TypedEventBus.get().emit(event);
        } catch (Exception ignored) {
            // Timer.schedule may throw without full LibGDX runtime
        }
    }

    @Test
    void p1WinsWhenPieceMovesToLastRow() {
        emitIgnoringTimer(new PieceMovedEvent(
                "p1", PieceAlignment.P1, 3, 0, 4, 0,
                PieceMovedEvent.MovementType.ACTIVE, "move"));

        assertTrue(GameManager.isInteractionsLocked());
    }

    @Test
    void p2WinsWhenPieceMovesToRow0() {
        emitIgnoringTimer(new PieceMovedEvent(
                "p2", PieceAlignment.P2, 1, 0, 0, 0,
                PieceMovedEvent.MovementType.ACTIVE, "move"));

        assertTrue(GameManager.isInteractionsLocked());
    }

    @Test
    void noWinWhenPieceMovesToMiddleRow() {
        TypedEventBus.get().emit(new PieceMovedEvent(
                "p1", PieceAlignment.P1, 1, 0, 2, 0,
                PieceMovedEvent.MovementType.ACTIVE, "move"));

        assertFalse(GameManager.isInteractionsLocked());
    }

    @Test
    void neutralPieceDoesNotTriggerWin() {
        TypedEventBus.get().emit(new PieceMovedEvent(
                "n1", PieceAlignment.NEUTRAL, 3, 0, 4, 0,
                PieceMovedEvent.MovementType.ACTIVE, "move"));

        assertFalse(GameManager.isInteractionsLocked());
    }

    @Test
    void p1WinsOnSpawnAtLastRow() {
        emitIgnoringTimer(new PieceSpawnedEvent("p1", PieceAlignment.P1, 4, 0));

        assertTrue(GameManager.isInteractionsLocked());
    }

    @Test
    void winOnlyTriggersOnce() {
        emitIgnoringTimer(new PieceMovedEvent(
                "p1", PieceAlignment.P1, 3, 0, 4, 0,
                PieceMovedEvent.MovementType.ACTIVE, "move"));
        assertTrue(GameManager.isInteractionsLocked());

        // Unlock manually to detect if second event would re-lock
        GameManager.unlockInteractions();

        // Second event should NOT trigger win again (gameWon is already true)
        emitIgnoringTimer(new PieceMovedEvent(
                "p2", PieceAlignment.P2, 1, 0, 0, 0,
                PieceMovedEvent.MovementType.ACTIVE, "move"));
        assertFalse(GameManager.isInteractionsLocked());
    }
}
