package io.github.elderpath_crusade.abilities.data;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.systems.GridIndexSystem;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TypedEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for StormAction's rework: it needs to hit an exact chosen tile
 * (not just its neighbors), which required a new "UnitAt" selector — nothing previously
 * resolved "the unit standing on this specific tile, if any".
 */
class TargetSelectorResolverUnitAtTest {

    private Engine engine;
    private GridIndexSystem gridIndex;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        engine = GameContext.get().getEcsEngine();
        gridIndex = engine.getSystem(GridIndexSystem.class);
    }

    private Entity buildPiece(PieceAlignment alignment, int row, int col) {
        Entity piece = engine.createEntity();
        piece.add(new AlignmentComponent().set(alignment));
        piece.add(new PositionComponent().set(row, col));
        engine.addEntity(piece);
        gridIndex.onEntitySpawned(piece, row, col);
        return piece;
    }

    @Test
    void unitAt_returnsOccupant_whenTileOccupied() {
        Entity target = buildPiece(PieceAlignment.P2, 3, 4);
        TargetSelector selector = new TargetSelector("UnitAt", Map.of("row", 3, "col", 4));

        List<Entity> resolved = TargetSelectorResolver.resolve(selector, null, new ExpressionContext());
        assertEquals(List.of(target), resolved);
    }

    @Test
    void unitAt_returnsEmpty_whenTileEmpty() {
        TargetSelector selector = new TargetSelector("UnitAt", Map.of("row", 9, "col", 9));
        List<Entity> resolved = TargetSelectorResolver.resolve(selector, null, new ExpressionContext());
        assertTrue(resolved.isEmpty());
    }

    @Test
    void unitAt_resolvesRowColFromContextExpressions() {
        Entity target = buildPiece(PieceAlignment.P1, 1, 2);
        ExpressionContext ctx = new ExpressionContext();
        ctx.set("$chosen.row", 1);
        ctx.set("$chosen.col", 2);

        TargetSelector selector = new TargetSelector("UnitAt", Map.of("row", "$chosen.row", "col", "$chosen.col"));
        List<Entity> resolved = TargetSelectorResolver.resolve(selector, null, ctx);
        assertEquals(List.of(target), resolved);
    }

    @Test
    void adjacentUnitsAt_withoutAlignmentFilter_hitsBothAlignments() {
        Entity caster = buildPiece(PieceAlignment.P1, 5, 5); // center (not returned by AdjacentUnitsAt itself)
        Entity friendly = buildPiece(PieceAlignment.P1, 5, 6);
        Entity enemy = buildPiece(PieceAlignment.P2, 4, 5);

        TargetSelector selector = new TargetSelector("AdjacentUnitsAt", Map.of("row", 5, "col", 5));
        List<Entity> resolved = TargetSelectorResolver.resolve(selector, caster, new ExpressionContext());

        assertTrue(resolved.contains(friendly), "StormAction's blast must hit friendly units too");
        assertTrue(resolved.contains(enemy), "StormAction's blast must hit enemy units too");
    }

    @Test
    void adjacentUnitsAt_exclude_omitsThatSpecificEntity() {
        Entity attacker = buildPiece(PieceAlignment.P1, 5, 5);
        Entity primaryTarget = buildPiece(PieceAlignment.P2, 5, 6);
        Entity otherEnemy = buildPiece(PieceAlignment.P2, 4, 5);

        ExpressionContext ctx = new ExpressionContext();
        ctx.set("$event.defenderEntity", primaryTarget);

        TargetSelector selector = new TargetSelector("AdjacentUnitsAt",
                Map.of("row", 5, "col", 5, "alignment", "Enemy", "exclude", "$event.defenderEntity"));
        List<Entity> resolved = TargetSelectorResolver.resolve(selector, attacker, ctx);

        assertFalse(resolved.contains(primaryTarget),
                "CleaveAttack must not re-hit the piece already damaged by the main attack");
        assertTrue(resolved.contains(otherEnemy), "other adjacent enemies must still be hit");
    }
}
