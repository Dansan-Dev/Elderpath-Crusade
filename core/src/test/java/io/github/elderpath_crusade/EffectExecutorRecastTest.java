package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.abilities.data.Condition;
import io.github.elderpath_crusade.abilities.data.EffectExecutor;
import io.github.elderpath_crusade.abilities.data.EffectNode;
import io.github.elderpath_crusade.abilities.data.ExpressionContext;
import io.github.elderpath_crusade.abilities.data.TargetSelector;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TypedEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for Recast — "Chain Lightning"'s "if it dies, recast on a random
 * piece within 1 tile of it" mechanic. Covers natural termination when a chain of kills
 * runs out of nearby targets, and the maxChains safety cap when a condition would
 * otherwise hold forever.
 */
class EffectExecutorRecastTest {

    private Engine engine;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        engine = GameContext.get().getEcsEngine();
    }

    private Entity buildEnemy(int row, int col, int health) {
        Entity e = engine.createEntity();
        e.add(new AlignmentComponent().set(PieceAlignment.P2));
        e.add(new PositionComponent().set(row, col));
        e.add(new StatsComponent().set(1, health, 1, 1, 1));
        engine.addEntity(e);
        return e;
    }

    @Test
    void chainsThroughEveryReachableTarget_thenStopsWhenNoneRemain() {
        Entity e1 = buildEnemy(0, 1, 1);
        Entity e2 = buildEnemy(0, 2, 1); // adjacent to e1
        Entity e3 = buildEnemy(0, 3, 1); // adjacent to e2, not to e1

        ExpressionContext ctx = new ExpressionContext();
        Map<String, Object> state = new HashMap<>();

        // Initial hit on e1 (mirrors ChainLightning's first Damage effect).
        EffectExecutor.execute(new EffectNode("Damage", Map.of("amount", 2)), List.of(e1), null, ctx, state);
        assertTrue(e1.getComponent(StatsComponent.class).currentHealth <= 0);

        EffectNode recast = new EffectNode("Recast", Map.of(
                "condition", new Condition("Compare", Map.of("left", "$lastDamage.targetDied", "op", "==", "right", true)),
                "newTargets", new TargetSelector("RandomUnitInRange", Map.of(
                        "row", "$lastDamage.row", "col", "$lastDamage.col", "range", 1)),
                "maxChains", 10,
                "effects", List.of(new EffectNode("Damage", Map.of("amount", 2)))
        ));
        EffectExecutor.execute(recast, List.of(), null, ctx, state);

        assertTrue(e2.getComponent(StatsComponent.class).currentHealth <= 0, "e2 should have been chained into");
        assertTrue(e3.getComponent(StatsComponent.class).currentHealth <= 0, "e3 should have been chained into");
    }

    @Test
    void neverEndingCondition_isBoundedByMaxChains() {
        Entity owner = engine.createEntity();
        owner.add(new AlignmentComponent().set(PieceAlignment.P1));
        owner.add(new PositionComponent().set(0, 0));
        engine.addEntity(owner);
        buildEnemy(0, 1, 100); // never dies from this test's effects — condition stays true forever

        ExpressionContext ctx = new ExpressionContext();
        Map<String, Object> state = new HashMap<>();

        EffectNode recast = new EffectNode("Recast", Map.of(
                "condition", new Condition("Always", Map.of()),
                "newTargets", new TargetSelector("AllEnemyUnits"),
                "maxChains", 3,
                "effects", List.of(new EffectNode("ModifyState", Map.of("key", "hits", "operation", "Add", "value", 1)))
        ));
        EffectExecutor.execute(recast, List.of(), owner, ctx, state);

        assertEquals(3, state.get("hits"), "must stop at the authored maxChains even though the condition never becomes false");
    }
}
