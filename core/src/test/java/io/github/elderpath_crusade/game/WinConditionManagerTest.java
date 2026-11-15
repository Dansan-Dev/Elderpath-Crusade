package io.github.elderpath_crusade.game;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.GameWonEvent;
import io.github.elderpath_crusade.events.PieceMovedEvent;
import io.github.elderpath_crusade.events.PieceSpawnedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.match.BoardGrid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WinConditionManagerTest {
    private WinConditionManager manager;
    private final List<GameWonEvent> wins = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext ctx = GameContext.createHeadless();
        ctx.setActiveGrid(new BoardGrid(5, 3));
        manager = ctx.getWinConditionManager();
        manager.resetSession();
        wins.clear();
        TypedEventBus.get().register(GameWonEvent.class, wins::add);
    }

    @AfterEach
    void tearDown() {
        TypedEventBus.get().clear();
    }

    private static PieceMovedEvent move(PieceAlignment owner, int toRow) {
        return new PieceMovedEvent("p", owner, 0, 0, toRow, 0, PieceMovedEvent.MovementType.ACTIVE, "move");
    }

    @Test
    void noWinnerBeforeAnyCondition() {
        assertNull(manager.getWinner());
        assertTrue(wins.isEmpty());
    }

    @Test
    void p1ReachingLastRow_winsOnMove() {
        TypedEventBus.get().emit(move(PieceAlignment.P1, 4));
        assertEquals(PieceAlignment.P1, manager.getWinner());
        assertEquals(1, wins.size());
        assertEquals(PieceAlignment.P1, wins.get(0).winner());
    }

    @Test
    void p2ReachingRowZero_winsOnMove() {
        TypedEventBus.get().emit(move(PieceAlignment.P2, 0));
        assertEquals(PieceAlignment.P2, manager.getWinner());
        assertEquals(PieceAlignment.P2, wins.get(0).winner());
    }

    @Test
    void spawningOnGoalRow_wins() {
        TypedEventBus.get().emit(new PieceSpawnedEvent("p", PieceAlignment.P2, 0, 1));
        assertEquals(PieceAlignment.P2, manager.getWinner());
        assertEquals(1, wins.size());
    }

    @Test
    void ownHomeRowIsNotAGoal() {
        TypedEventBus.get().emit(move(PieceAlignment.P1, 0));
        TypedEventBus.get().emit(move(PieceAlignment.P2, 4));
        TypedEventBus.get().emit(move(PieceAlignment.P1, 2));
        assertNull(manager.getWinner());
        assertTrue(wins.isEmpty());
    }

    @Test
    void neutralPieceNeverWins() {
        TypedEventBus.get().emit(move(PieceAlignment.NEUTRAL, 4));
        TypedEventBus.get().emit(move(PieceAlignment.NEUTRAL, 0));
        assertNull(manager.getWinner());
    }

    @Test
    void wonEventEmittedOnce_evenIfConditionKeepsHolding() {
        TypedEventBus.get().emit(move(PieceAlignment.P1, 4));
        TypedEventBus.get().emit(move(PieceAlignment.P1, 4));
        TypedEventBus.get().emit(move(PieceAlignment.P2, 0));
        assertEquals(1, wins.size());
        assertEquals(PieceAlignment.P1, manager.getWinner());
    }

    @Test
    void noActiveGrid_neverWins() {
        GameContext.get().setActiveGrid(null);
        TypedEventBus.get().emit(move(PieceAlignment.P1, 4));
        assertNull(manager.getWinner());
        assertTrue(wins.isEmpty());
    }

    @Test
    void reset_allowsANewWin() {
        TypedEventBus.get().emit(move(PieceAlignment.P1, 4));
        manager.reset();
        assertNull(manager.getWinner());
        TypedEventBus.get().emit(move(PieceAlignment.P2, 0));
        assertEquals(PieceAlignment.P2, manager.getWinner());
        assertEquals(2, wins.size());
    }

    @Test
    void initialize_calledTwice_doesNotDoubleRegister() {
        manager.initialize();
        TypedEventBus.get().emit(move(PieceAlignment.P1, 4));
        assertEquals(1, wins.size());
    }
}
