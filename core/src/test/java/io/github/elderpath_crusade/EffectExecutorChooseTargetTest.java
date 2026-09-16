package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.abilities.data.EffectExecutor;
import io.github.elderpath_crusade.abilities.data.EffectNode;
import io.github.elderpath_crusade.abilities.data.ExpressionContext;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
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
 * Regression coverage for RogueFreeStrike: it must let the player pick which adjacent
 * enemy to strike rather than always auto-picking one, and must fizzle without
 * prompting at all when there's nothing to strike.
 */
class EffectExecutorChooseTargetTest {

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
        GameContext.get().setActiveBoard(board);
    }

    private Entity buildPiece(PieceAlignment alignment, int row, int col) {
        Entity e = engine.createEntity();
        e.add(new AlignmentComponent().set(alignment));
        e.add(new PositionComponent().set(row, col));
        e.add(new StatsComponent().set(1, 3, 1, 1, 1));
        engine.addEntity(e);
        gridIndex.onEntitySpawned(e, row, col);
        Plot plot = mock(Plot.class);
        when(board.getPlotAtPos(row, col)).thenReturn(plot);
        return e;
    }

    private EffectNode chooseTargetEffect() {
        return new EffectNode("ChooseTarget", Map.of(
                "candidates", "AdjacentEnemies",
                "effects", List.of(new EffectNode("Damage", Map.of("target", "$chosen", "amount", "$self.damage")))
        ));
    }

    @Test
    void multipleAdjacentEnemies_promptsThePlayer_doesNotAutoResolve() {
        Entity rogue = buildPiece(PieceAlignment.P1, 2, 2);
        Entity enemyA = buildPiece(PieceAlignment.P2, 2, 3);
        Entity enemyB = buildPiece(PieceAlignment.P2, 1, 2);

        ExpressionContext ctx = new ExpressionContext();
        ctx.set("$self.damage", 1);

        EffectExecutor.execute(chooseTargetEffect(), List.of(), rogue, ctx, new HashMap<>());

        assertTrue(GameContext.get().getInteractionManager().hasActiveSelection(),
                "must prompt when there's a real choice to make");
        assertEquals(3, enemyA.getComponent(StatsComponent.class).currentHealth, "no damage dealt before a pick is made");
        assertEquals(3, enemyB.getComponent(StatsComponent.class).currentHealth);
    }

    @Test
    void noAdjacentEnemies_fizzlesSilently_neverPrompts() {
        Entity rogue = buildPiece(PieceAlignment.P1, 2, 2);

        EffectExecutor.execute(chooseTargetEffect(), List.of(), rogue, new ExpressionContext(), new HashMap<>());

        assertFalse(GameContext.get().getInteractionManager().hasActiveSelection());
    }
}
