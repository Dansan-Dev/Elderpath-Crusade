package io.github.elderpath_crusade.ecs.components;

import com.badlogic.ashley.core.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds ability name strings for an entity (reference only).
 * OOP ability instances have been removed; see AbilityInstanceComponent for data-driven abilities.
 */
public class AbilityComponent implements Component {
    public final List<String> abilityNames = new ArrayList<>();

    public AbilityComponent add(String abilityName) {
        abilityNames.add(abilityName);
        return this;
    }

    public void clear() {
        abilityNames.clear();
    }
}
