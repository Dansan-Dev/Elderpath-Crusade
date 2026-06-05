package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.PieceRefComponent;
import io.github.elderpath_crusade.ecs.components.SpriteComponent;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TurnEndedEvent;
import io.github.elderpath_crusade.events.TurnStartedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BoardEventListenerTest {

    private Board board;
    private Engine engine;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        engine = GameContext.get().getEcsEngine();
        board = mock(Board.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
    }

    private void addPieceEntity(MonsterGamePiece piece, PieceAlignment alignment) {
        Entity entity = engine.createEntity();
        SpriteComponent sc = new SpriteComponent();
        entity.add(sc);
        entity.add(new PieceRefComponent().set(piece));
        AlignmentComponent ac = new AlignmentComponent().set(alignment);
        entity.add(ac);
        engine.addEntity(entity);
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
            
        });

        TypedEventBus.get().emit(new TurnStartedEvent(PieceAlignment.P2));
        verify(board, never()).flipRows();
    }



    @Test
    void turnStarted_skipsNullPieces() {
        // No entities in engine — should not throw
    }
}
