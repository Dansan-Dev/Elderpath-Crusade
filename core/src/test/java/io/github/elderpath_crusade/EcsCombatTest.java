package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.ecs.systems.CombatSystem;
import io.github.elderpath_crusade.events.TypedEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EcsCombatTest {

    private Engine engine;
    private CombatSystem combatSystem;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        engine = GameContext.get().getEcsEngine();
        combatSystem = GameContext.get().getCombatSystem();
    }

    private Entity createEntity(int cost, int maxHealth, int damage, int speed, int actions) {
        Entity entity = engine.createEntity();
        entity.add(new StatsComponent().set(cost, maxHealth, damage, speed, actions));
        engine.addEntity(entity);
        return entity;
    }

    @Test
    void applyDamage_reduceHealth() {
        Entity entity = createEntity(1, 10, 3, 1, 1);
        boolean dead = combatSystem.applyDamage(entity, 3);
        assertEquals(7, entity.getComponent(StatsComponent.class).currentHealth);
        assertFalse(dead);
    }

    @Test
    void applyDamage_lethalDamage_returnsTrue() {
        Entity entity = createEntity(1, 5, 2, 1, 1);
        boolean dead = combatSystem.applyDamage(entity, 5);
        assertEquals(0, entity.getComponent(StatsComponent.class).currentHealth);
        assertTrue(dead);
    }

    @Test
    void applyDamage_overkill_returnsTrue() {
        Entity entity = createEntity(1, 3, 2, 1, 1);
        boolean dead = combatSystem.applyDamage(entity, 10);
        assertEquals(-7, entity.getComponent(StatsComponent.class).currentHealth);
        assertTrue(dead);
    }

    @Test
    void resolveAttack_appliesDamageToDefender() {
        Entity attacker = createEntity(1, 10, 4, 1, 1);
        Entity defender = createEntity(1, 10, 2, 1, 1);
        combatSystem.resolveAttack(attacker, defender, 4);
        assertEquals(6, defender.getComponent(StatsComponent.class).currentHealth);
    }

    @Test
    void resolveAttack_lethal_returnsTrue() {
        Entity attacker = createEntity(1, 10, 5, 1, 1);
        Entity defender = createEntity(1, 2, 1, 1, 1);
        boolean dead = combatSystem.resolveAttack(attacker, defender, 5);
        assertTrue(dead);
        assertTrue(defender.getComponent(StatsComponent.class).currentHealth <= 0);
    }
}
