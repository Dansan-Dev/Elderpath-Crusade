package io.github.elderpath_crusade.ecs.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import io.github.elderpath_crusade.ecs.components.StatsComponent;

/**
 * ECS system for combat resolution. Reads attacker/defender stats from components
 * and applies damage. Returns whether the defender died.
 */
public class CombatSystem extends EntitySystem {

    public CombatSystem() {}

    /**
     * Resolve an attack between two entities using their StatsComponents.
     * @param attacker the attacking entity
     * @param defender the defending entity
     * @param damage the damage to deal (typically attacker's effective damage including modifiers)
     * @return true if the defender died (health <= 0)
     */
    public boolean resolveAttack(Entity attacker, Entity defender, int damage) {
        if (attacker == null || defender == null) return false;

        StatsComponent defenderStats = defender.getComponent(StatsComponent.class);
        if (defenderStats == null) return false;

        defenderStats.currentHealth -= damage;
        return defenderStats.isDead();
    }

    /**
     * Apply raw damage to an entity (no attacker context needed).
     * @param target the entity taking damage
     * @param damage the amount of damage
     * @return true if the target died
     */
    public boolean applyDamage(Entity target, int damage) {
        if (target == null || damage <= 0) return false;

        StatsComponent stats = target.getComponent(StatsComponent.class);
        if (stats == null) return false;

        stats.currentHealth -= damage;
        return stats.isDead();
    }
}
