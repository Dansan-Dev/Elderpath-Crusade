package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.abilities.data.EffectExecutor;
import io.github.elderpath_crusade.abilities.data.EffectNode;
import io.github.elderpath_crusade.abilities.data.ExpressionContext;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.ecs.components.TerrainComponent;
import io.github.elderpath_crusade.ecs.systems.GridIndexSystem;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.Plot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for Charger's PushOnAttack rework: it must push the defender back
 * AND advance the attacker into the tile the defender vacated (not just push), and a
 * push blocked by terrain must deal 1 damage instead of moving either piece.
 */
class EffectExecutorPushAndAdvanceTest {

    private Engine engine;
    private GridIndexSystem gridIndex;
    private Board board;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        engine = GameContext.get().getEcsEngine();
        gridIndex = engine.getSystem(GridIndexSystem.class);

        board = mock(Board.class);
        when(board.getROWS()).thenReturn(5);
        when(board.getCOLS()).thenReturn(5);
        GameContext.get().setActiveBoard(board);
    }

    private Entity buildPiece(PieceAlignment alignment, int row, int col) {
        Entity e = engine.createEntity();
        e.add(new AlignmentComponent().set(alignment));
        e.add(new PositionComponent().set(row, col));
        e.add(new StatsComponent().set(1, 3, 1, 1, 1));
        engine.addEntity(e);
        gridIndex.onEntitySpawned(e, row, col);
        when(board.getEntityAtPos(row, col)).thenReturn(e);
        when(board.isOccupied(row, col)).thenReturn(true);
        return e;
    }

    private void makeEmpty(int row, int col) {
        when(board.getEntityAtPos(row, col)).thenReturn(null);
        when(board.isOccupied(row, col)).thenReturn(false);
    }

    private void placeTerrain(int row, int col) {
        Entity terrain = engine.createEntity();
        terrain.add(new TerrainComponent());
        terrain.add(new PositionComponent().set(row, col));
        engine.addEntity(terrain);
        when(board.getEntityAtPos(row, col)).thenReturn(terrain);
        when(board.isOccupied(row, col)).thenReturn(true);
    }

    private void trackMove(Entity piece) {
        // MovementSystem.executeForcedMove uses board.moveEntity/isValidMove; mirror the
        // occupancy bookkeeping a real Board would do so the next check sees it correctly.
        doAnswer(inv -> {
            int fromRow = inv.getArgument(0);
            int fromCol = inv.getArgument(1);
            Entity movedEntity = inv.getArgument(2);
            int toRow = inv.getArgument(3);
            int toCol = inv.getArgument(4);
            when(board.getEntityAtPos(fromRow, fromCol)).thenReturn(null);
            when(board.isOccupied(fromRow, fromCol)).thenReturn(false);
            when(board.getEntityAtPos(toRow, toCol)).thenReturn(movedEntity);
            when(board.isOccupied(toRow, toCol)).thenReturn(true);
            return null;
        }).when(board).moveEntity(anyInt(), anyInt(), eq(piece), anyInt(), anyInt());
    }

    @Test
    void pushIntoEmptyTile_pushesDefenderAndAdvancesAttacker() {
        Entity charger = buildPiece(PieceAlignment.P1, 2, 2);
        Entity defender = buildPiece(PieceAlignment.P2, 2, 3);
        makeEmpty(2, 4); // push destination
        trackMove(defender);
        trackMove(charger);

        EffectExecutor.execute(new EffectNode("PushAndAdvance", Map.of()),
                List.of(defender), charger, new ExpressionContext(), new HashMap<>());

        assertEquals(4, defender.getComponent(PositionComponent.class).col, "defender pushed back one tile");
        assertEquals(3, charger.getComponent(PositionComponent.class).col, "attacker advances into the vacated tile");
    }

    @Test
    void pushBlockedByTerrain_dealsOneDamageInstead_neitherPieceMoves() {
        Entity charger = buildPiece(PieceAlignment.P1, 2, 2);
        Entity defender = buildPiece(PieceAlignment.P2, 2, 3);
        placeTerrain(2, 4);

        EffectExecutor.execute(new EffectNode("PushAndAdvance", Map.of()),
                List.of(defender), charger, new ExpressionContext(), new HashMap<>());

        assertEquals(2, defender.getComponent(StatsComponent.class).currentHealth, "3 - 1 damage");
        assertEquals(3, defender.getComponent(PositionComponent.class).col, "defender did not move");
        assertEquals(2, charger.getComponent(PositionComponent.class).col, "attacker did not advance");
    }

    @Test
    void pushBlockedByAnotherUnit_fizzles_noDamageNoMovement() {
        Entity charger = buildPiece(PieceAlignment.P1, 2, 2);
        Entity defender = buildPiece(PieceAlignment.P2, 2, 3);
        buildPiece(PieceAlignment.P1, 2, 4); // blocks the push destination

        EffectExecutor.execute(new EffectNode("PushAndAdvance", Map.of()),
                List.of(defender), charger, new ExpressionContext(), new HashMap<>());

        assertEquals(3, defender.getComponent(StatsComponent.class).currentHealth, "no damage on a unit-blocked push");
        assertEquals(3, defender.getComponent(PositionComponent.class).col);
        assertEquals(2, charger.getComponent(PositionComponent.class).col);
    }

    @Test
    void pushOffTheBoard_fizzles() {
        // Charger at col 1, defender at col 0 — pushing further away goes to col -1, off-board.
        Entity charger = buildPiece(PieceAlignment.P1, 2, 1);
        Entity defender = buildPiece(PieceAlignment.P2, 2, 0);

        EffectExecutor.execute(new EffectNode("PushAndAdvance", Map.of()),
                List.of(defender), charger, new ExpressionContext(), new HashMap<>());

        assertEquals(3, defender.getComponent(StatsComponent.class).currentHealth);
        assertEquals(0, defender.getComponent(PositionComponent.class).col);
        assertEquals(1, charger.getComponent(PositionComponent.class).col);
    }
}
