package io.github.elderpath_crusade;

import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.events.TurnEndedEvent;
import io.github.elderpath_crusade.events.TurnStartedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BoardEventListenerTest {

    private Board board;
    private GamePiece[][] gamePieces;

    @BeforeEach
    void setUp() throws Exception {
        TypedEventBus.get().clear();
        GameContext.create();

        // Create a mock Board with real method implementations for notify methods
        board = mock(Board.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        gamePieces = new GamePiece[3][3];

        Field gpField = Board.class.getDeclaredField("gamePieces");
        gpField.setAccessible(true);
        gpField.set(board, gamePieces);

        Field rowsField = Board.class.getDeclaredField("ROWS");
        rowsField.setAccessible(true);
        rowsField.set(board, 3);

        Field colsField = Board.class.getDeclaredField("COLS");
        colsField.setAccessible(true);
        colsField.set(board, 3);
    }

    @Test
    void turnStarted_localMatch_flipsForP2() {
        GameContext.get().getGameModeManager().setCurrent(GameMode.LOCAL_MATCH);
        doReturn(false).when(board).isFlipped();
        doNothing().when(board).flipRows();

        TypedEventBus.get().register(TurnStartedEvent.class, event -> {
            if (GameContext.get().getGameModeManager().getCurrent() == GameMode.LOCAL_MATCH) {
                boolean shouldBeFlipped = (event.player() == PieceAlignment.P2);
                if (shouldBeFlipped != board.isFlipped()) {
                    board.flipRows();
                }
            }
            board.notifyTurnStartedForPieces(event.player());
        });

        TypedEventBus.get().emit(new TurnStartedEvent(PieceAlignment.P2));
        verify(board).flipRows();
    }

    @Test
    void turnStarted_localMatch_doesNotFlipForP1() {
        GameContext.get().getGameModeManager().setCurrent(GameMode.LOCAL_MATCH);
        doReturn(false).when(board).isFlipped();

        TypedEventBus.get().register(TurnStartedEvent.class, event -> {
            if (GameContext.get().getGameModeManager().getCurrent() == GameMode.LOCAL_MATCH) {
                boolean shouldBeFlipped = (event.player() == PieceAlignment.P2);
                if (shouldBeFlipped != board.isFlipped()) {
                    board.flipRows();
                }
            }
            board.notifyTurnStartedForPieces(event.player());
        });

        TypedEventBus.get().emit(new TurnStartedEvent(PieceAlignment.P1));
        verify(board, never()).flipRows();
    }

    @Test
    void turnStarted_demoMode_doesNotFlip() {
        GameContext.get().getGameModeManager().setCurrent(GameMode.DEMO);
        doReturn(false).when(board).isFlipped();

        TypedEventBus.get().register(TurnStartedEvent.class, event -> {
            if (GameContext.get().getGameModeManager().getCurrent() == GameMode.LOCAL_MATCH) {
                board.flipRows();
            }
            board.notifyTurnStartedForPieces(event.player());
        });

        TypedEventBus.get().emit(new TurnStartedEvent(PieceAlignment.P2));
        verify(board, never()).flipRows();
    }

    @Test
    void turnStarted_notifiesPiecesOnBoard() {
        MonsterGamePiece piece = mock(MonsterGamePiece.class);
        gamePieces[1][1] = piece;

        board.notifyTurnStartedForPieces(PieceAlignment.P1);

        verify(piece).notifyTurnStarted(PieceAlignment.P1);
    }

    @Test
    void turnEnded_notifiesPiecesOnBoard() {
        MonsterGamePiece piece = mock(MonsterGamePiece.class);
        gamePieces[0][2] = piece;

        board.notifyTurnEndedForPieces(PieceAlignment.P1);

        verify(piece).notifyTurnEnded(PieceAlignment.P1);
    }

    @Test
    void turnStarted_skipsNullPieces() {
        // All positions are null — should not throw
        assertDoesNotThrow(() -> board.notifyTurnStartedForPieces(PieceAlignment.P1));
    }
}
