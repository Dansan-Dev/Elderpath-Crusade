package io.github.elderpath_crusade.ecs.components;

import com.badlogic.ashley.core.Component;

public class ComputedStatsComponent implements Component {
    public int damage, speed, actions, maxHealth, cost, range;
    public boolean ignoreTerrainAsBlockers, ignoreFriendlyAsBlockers, ignoreHostileAsBlockers;
    public boolean dirty = true;
}
