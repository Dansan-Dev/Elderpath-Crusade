package io.github.elderpath_crusade.ecs.components;

import com.badlogic.ashley.core.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds ability names/types for an entity. Full ability logic remains in the
 * existing ability system for now; this component enables ECS queries.
 */
public class AbilityComponent implements Component {
    public final List<String> abilityNames = new ArrayList<>();

    public AbilityComponent add(String abilityName) {
        abilityNames.add(abilityName);
        return this;
    }
}
