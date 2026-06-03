package io.github.elderpath_crusade.ecs.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import io.github.elderpath_crusade.ecs.components.ComputedStatsComponent;
import io.github.elderpath_crusade.ecs.components.HealIntentComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;

/**
 * Processes HealIntentComponent: heals entity capped at effective max health, removes intent.
 */
public class HealSystem extends EntitySystem {

    private Family family;

    @Override
    public void addedToEngine(Engine engine) {
        family = Family.all(HealIntentComponent.class, StatsComponent.class, ComputedStatsComponent.class).get();
    }

    @Override
    public void update(float deltaTime) {
        ImmutableArray<Entity> entities = getEngine().getEntitiesFor(family);
        for (int i = entities.size() - 1; i >= 0; i--) {
            Entity entity = entities.get(i);
            HealIntentComponent heal = entity.getComponent(HealIntentComponent.class);
            StatsComponent stats = entity.getComponent(StatsComponent.class);
            ComputedStatsComponent computed = entity.getComponent(ComputedStatsComponent.class);

            int newHealth = Math.min(stats.currentHealth + heal.amount, computed.maxHealth);
            stats.currentHealth = newHealth;
            entity.remove(HealIntentComponent.class);
        }
    }
}
