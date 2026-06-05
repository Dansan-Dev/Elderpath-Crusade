package io.github.elderpath_crusade.ecs.components;

import com.badlogic.ashley.core.Component;
import io.github.elderpath_crusade.abilities.stats.StatsAccumulator;

/**
 * Holds the StatsAccumulator for an entity.
 */
public class ModifierComponent implements Component {
    public final StatsAccumulator accumulator = new StatsAccumulator();
}
