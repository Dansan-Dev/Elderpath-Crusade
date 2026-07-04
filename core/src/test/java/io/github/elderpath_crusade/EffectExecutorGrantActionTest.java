package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.abilities.data.EffectExecutor;
import io.github.elderpath_crusade.abilities.data.EffectNode;
import io.github.elderpath_crusade.abilities.data.ExpressionContext;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.events.TypedEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression coverage for BUG-016 (Wind Spirit half): BoostAction must grant a one-time
 * bonus to remainingActions, not overwrite it and not route through the permanent
 * stats-modifier accumulator (AddModifier).
 */
class EffectExecutorGrantActionTest {

    private Entity target;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        Engine engine = GameContext.get().getEcsEngine();

        target = engine.createEntity();
        target.add(new StatsComponent().set(1, 2, 0, 1, 1));
        target.getComponent(StatsComponent.class).remainingActions = 0;
        engine.addEntity(target);
    }

    @Test
    void grantAction_addsToRemainingActions_ratherThanOverwriting() {
        EffectNode grant = new EffectNode("GrantAction", Map.of("target", "$chosen", "amount", 1));

        EffectExecutor.execute(grant, List.of(target), null, new ExpressionContext(), new HashMap<>());
        assertEquals(1, target.getComponent(StatsComponent.class).remainingActions);

        EffectExecutor.execute(grant, List.of(target), null, new ExpressionContext(), new HashMap<>());
        assertEquals(2, target.getComponent(StatsComponent.class).remainingActions);
    }
}
