package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.abilities.stats.StatsAccumulator;
import io.github.elderpath_crusade.abilities.stats.StatsModifier;
import io.github.elderpath_crusade.ecs.components.ModifierComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.events.TypedEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EcsStatsSyncTest {

    private Engine engine;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        engine = GameContext.get().getEcsEngine();
    }

    @Test
    void statsComponent_readsValues() {
        StatsComponent stats = new StatsComponent().set(2, 5, 3, 1, 2);

        assertEquals(2, stats.cost);
        assertEquals(5, stats.maxHealth);
        assertEquals(3, stats.damage);
        assertEquals(1, stats.speed);
        assertEquals(2, stats.actions);
        assertEquals(5, stats.currentHealth);
    }

    @Test
    void statsComponent_dealDamage() {
        StatsComponent stats = new StatsComponent().set(2, 5, 3, 1, 2);
        stats.currentHealth -= 2;

        assertEquals(3, stats.currentHealth);
    }

    @Test
    void statsComponent_setRemainingActions() {
        StatsComponent stats = new StatsComponent().set(2, 5, 3, 1, 2);
        stats.remainingActions = 1;

        assertEquals(1, stats.remainingActions);
    }

    @Test
    void modifierComponent_accumulator_addsModifier() {
        Entity entity = engine.createEntity();
        entity.add(new StatsComponent().set(2, 5, 3, 1, 2));
        ModifierComponent mc = new ModifierComponent();
        entity.add(mc);
        engine.addEntity(entity);

        StatsModifier mod = new StatsModifier();
        mod.addDamage = 2;
        mc.accumulator.add(mod);

        assertTrue(mc.accumulator.has(mod));
        assertEquals(1, mc.accumulator.getAll().size());
    }

    @Test
    void statsModifier_applyInt_formula() {
        // effective = max(0, round((base + add) * (1 + mult)))
        int result = StatsModifier.applyInt(3, 2, 0.5f);
        // (3 + 2) * (1 + 0.5) = 5 * 1.5 = 7.5 -> round = 8
        assertEquals(8, result);
    }
}
