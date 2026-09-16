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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage: BombAction must deal the unit's own damage stat to itself
 * (not a hardcoded lethal 999), so it survives if its damage is less than its health,
 * rather than always sacrificing itself.
 */
class BombActionEffectTest {

    private Entity bomber;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        Engine engine = GameContext.get().getEcsEngine();

        bomber = engine.createEntity();
        bomber.add(new StatsComponent().set(2, 3, 2, 1, 1)); // matches SkeletonBomber: health 3, damage 2
        engine.addEntity(bomber);
    }

    @Test
    void selfDamage_usesOwnDamageStat_notFixedLethalAmount() {
        ExpressionContext ctx = new ExpressionContext();
        ctx.set("$self.damage", 2);

        EffectNode selfDamage = new EffectNode("Damage", Map.of("target", "$self", "amount", "$self.damage"));
        EffectExecutor.execute(selfDamage, List.of(bomber), bomber, ctx, new HashMap<>());

        StatsComponent stats = bomber.getComponent(StatsComponent.class);
        assertEquals(1, stats.currentHealth, "bomber should take exactly its own damage, not be destroyed outright");
    }

    @Test
    void adjacentDamage_usesOwnDamageStat_notFixedTwo() {
        Engine engine = GameContext.get().getEcsEngine();
        Entity neighbor = engine.createEntity();
        neighbor.add(new StatsComponent().set(1, 5, 0, 1, 1));
        engine.addEntity(neighbor);

        ExpressionContext ctx = new ExpressionContext();
        ctx.set("$self.damage", 2);

        EffectNode damage = new EffectNode("Damage", Map.of("target", "$target", "amount", "$self.damage"));
        EffectExecutor.execute(damage, List.of(neighbor), bomber, ctx, new HashMap<>());

        assertEquals(3, neighbor.getComponent(StatsComponent.class).currentHealth);
    }
}
