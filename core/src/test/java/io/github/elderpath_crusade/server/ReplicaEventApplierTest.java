package io.github.elderpath_crusade.server;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.IdentityComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.ecs.systems.PlayerSystem;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.*;
import io.github.elderpath_crusade.game_objects.board.Board;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies ReplicaEventApplier mutates the guest's own local ECS/board state correctly for
 * each relayed event type — the piece-spawn-from-CardPlayedEvent path (which needs real
 * PieceRegistry/CardFactory data) is exercised live rather than here; this covers the
 * state-mutation logic that has no asset dependency.
 */
class ReplicaEventApplierTest {
    private Board board;
    private Entity piece;
    private ReplicaEventApplier applier;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        board = mock(Board.class);
        GameContext.get().setActiveBoard(board);
        applier = new ReplicaEventApplier();

        piece = new Entity();
        piece.add(new PositionComponent().set(2, 3));
        piece.add(new AlignmentComponent().set(PieceAlignment.P1));
        piece.add(new IdentityComponent().set("piece-1", "TestPiece"));
        piece.add(new StatsComponent().set(2, 10, 3, 1, 1)); // cost, maxHealth, damage, speed, actions

        when(board.getEntityAtPos(2, 3)).thenReturn(piece);
    }

    @Test
    void pieceMoved_updatesPositionAndDelegatesToBoard() {
        when(board.getEntityAtPos(4, 5)).thenReturn(null);

        applier.apply(new PieceMovedEvent("piece-1", PieceAlignment.P1, 2, 3, 4, 5,
                PieceMovedEvent.MovementType.ACTIVE, "test"));

        PositionComponent pos = piece.getComponent(PositionComponent.class);
        assertEquals(4, pos.row);
        assertEquals(5, pos.col);
        verify(board).moveEntity(2, 3, piece, 4, 5);
    }

    @Test
    void pieceAttacked_reducesDefenderHealthByDamage() {
        applier.apply(new PieceAttackedEvent("attacker", PieceAlignment.P2, 0, 0, "piece-1", 2, 3, 4));
        assertEquals(6, piece.getComponent(StatsComponent.class).currentHealth);
    }

    @Test
    void pieceDamaged_reducesHealthByAmount() {
        applier.apply(new PieceDamagedEvent("piece-1", PieceAlignment.P1, 2, 3, 3));
        assertEquals(7, piece.getComponent(StatsComponent.class).currentHealth);
    }

    @Test
    void pieceHealed_clampsToMaxHealth() {
        piece.getComponent(StatsComponent.class).currentHealth = 9;
        applier.apply(new PieceHealedEvent("piece-1", PieceAlignment.P1, 2, 3, 5));
        assertEquals(10, piece.getComponent(StatsComponent.class).currentHealth);
    }

    @Test
    void pieceDied_removesFromBoard() {
        applier.apply(new PieceDiedEvent("piece-1", 2, 3));
        verify(board).removeEntityAtPos(2, 3);
    }

    @Test
    void manaChanged_setsPlayerSystemManaDirectly() {
        applier.apply(new ManaChangedEvent(PieceAlignment.P1, 7));
        assertEquals(7, GameContext.get().getEcsEngine().getSystem(PlayerSystem.class).getMana(PieceAlignment.P1));
    }

    @Test
    void actionsReset_restoresRemainingActionsForMatchingAlignmentOnly() {
        piece.getComponent(StatsComponent.class).remainingActions = 0;
        when(board.getROWS()).thenReturn(7);
        when(board.getCOLS()).thenReturn(5);

        applier.apply(new ActionsResetEvent(PieceAlignment.P2)); // different alignment — no change
        assertEquals(0, piece.getComponent(StatsComponent.class).remainingActions);

        applier.apply(new ActionsResetEvent(PieceAlignment.P1)); // matches piece's alignment
        assertEquals(1, piece.getComponent(StatsComponent.class).remainingActions);
    }

    @Test
    void turnStarted_setsCurrentPlayerWithoutSideEffects() {
        applier.apply(new TurnStartedEvent(PieceAlignment.P2));
        assertEquals(PieceAlignment.P2, GameContext.get().getTurnManager().getCurrentPlayer());
    }
}
