package io.github.elderpath_crusade.ecs.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import io.github.elderpath_crusade.abilities.stats.StatsModifier;
import io.github.elderpath_crusade.ecs.components.ComputedStatsComponent;
import io.github.elderpath_crusade.ecs.components.ModifierComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;

/**
 * Recomputes effective stats each frame from base stats + modifiers.
 * Always recomputes (no dirty-tracking optimization — modifier count is trivial per piece).
 */
public class ModifierResolutionSystem extends EntitySystem {

    private Family family;

    @Override
    public void addedToEngine(Engine engine) {
        family = Family.all(StatsComponent.class, ModifierComponent.class, ComputedStatsComponent.class).get();
    }

    @Override
    public void update(float deltaTime) {
        ImmutableArray<Entity> entities = getEngine().getEntitiesFor(family);
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            ComputedStatsComponent c = entity.getComponent(ComputedStatsComponent.class);
            StatsComponent base = entity.getComponent(StatsComponent.class);
            ModifierComponent mc = entity.getComponent(ModifierComponent.class);

            int addDamage = 0, addSpeed = 0, addActions = 0, addMaxHealth = 0, addCost = 0, addRange = 0;
            float multDamage = 0f, multSpeed = 0f, multActions = 0f, multMaxHealth = 0f, multCost = 0f, multRange = 0f;
            boolean terrain = false, friendly = false, hostile = false;

            for (StatsModifier m : mc.accumulator.getAll()) {
                addDamage += m.addDamage; multDamage += m.multDamage;
                addSpeed += m.addSpeed; multSpeed += m.multSpeed;
                addActions += m.addActions; multActions += m.multActions;
                addMaxHealth += m.addMaxHealth; multMaxHealth += m.multMaxHealth;
                addCost += m.addCost; multCost += m.multCost;
                addRange += m.addRange; multRange += m.multRange;
                if (m.ignoreTerrainAsBlockers) terrain = true;
                if (m.ignoreFriendlyUnitsAsBlockers) friendly = true;
                if (m.ignoreHostileUnitsAsBlockers) hostile = true;
            }

            c.damage = StatsModifier.applyInt(base.damage, addDamage, multDamage);
            c.speed = StatsModifier.applyInt(base.speed, addSpeed, multSpeed);
            c.actions = StatsModifier.applyInt(base.actions, addActions, multActions);
            c.maxHealth = StatsModifier.applyInt(base.maxHealth, addMaxHealth, multMaxHealth);
            c.cost = StatsModifier.applyInt(base.cost, addCost, multCost);
            c.range = StatsModifier.applyInt(0, addRange, multRange);
            c.ignoreTerrainAsBlockers = terrain;
            c.ignoreFriendlyAsBlockers = friendly;
            c.ignoreHostileAsBlockers = hostile;
            c.dirty = false;
        }
    }
}
