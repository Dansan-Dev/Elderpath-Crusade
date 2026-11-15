package io.github.elderpath_crusade.ecs.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.ecs.components.ComputedStatsComponent;
import io.github.elderpath_crusade.ecs.components.HealIntentComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HealSystemTest {

    private Engine engine;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        engine.addSystem(new HealSystem());
    }

    private Entity piece(int currentHealth, int baseMax, int computedMax) {
        Entity e = new Entity();
        StatsComponent stats = new StatsComponent().set(1, baseMax, 1, 1, 1);
        stats.currentHealth = currentHealth;
        e.add(stats);
        ComputedStatsComponent computed = new ComputedStatsComponent();
        computed.maxHealth = computedMax;
        e.add(computed);
        engine.addEntity(e);
        return e;
    }

    @Test
    void heal_addsAmount() {
        Entity e = piece(2, 5, 5);
        e.add(new HealIntentComponent().set(2));
        engine.update(0f);
        assertEquals(4, e.getComponent(StatsComponent.class).currentHealth);
    }

    @Test
    void heal_isCappedAtEffectiveMaxHealth() {
        Entity e = piece(4, 5, 5);
        e.add(new HealIntentComponent().set(10));
        engine.update(0f);
        assertEquals(5, e.getComponent(StatsComponent.class).currentHealth);
    }

    @Test
    void heal_capUsesComputedMaxNotBase() {
        Entity e = piece(5, 5, 7); // an aura raised max health
        e.add(new HealIntentComponent().set(10));
        engine.update(0f);
        assertEquals(7, e.getComponent(StatsComponent.class).currentHealth);
    }

    @Test
    void heal_removesTheIntentSoItAppliesOnce() {
        Entity e = piece(1, 5, 5);
        e.add(new HealIntentComponent().set(1));
        engine.update(0f);
        engine.update(0f);
        assertEquals(2, e.getComponent(StatsComponent.class).currentHealth);
        assertNull(e.getComponent(HealIntentComponent.class));
    }

    @Test
    void entityWithoutIntent_isUntouched() {
        Entity e = piece(3, 5, 5);
        engine.update(0f);
        assertEquals(3, e.getComponent(StatsComponent.class).currentHealth);
    }

    @Test
    void healingAboveMaxAlreadyOverhealed_clampsDown() {
        // Documents current behaviour: the cap is applied even when it lowers health.
        Entity e = piece(9, 5, 5);
        e.add(new HealIntentComponent().set(1));
        engine.update(0f);
        assertEquals(5, e.getComponent(StatsComponent.class).currentHealth);
    }
}
