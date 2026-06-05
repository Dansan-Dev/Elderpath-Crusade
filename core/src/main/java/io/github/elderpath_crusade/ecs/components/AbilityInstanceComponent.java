package io.github.elderpath_crusade.ecs.components;

import com.badlogic.ashley.core.Component;
import io.github.elderpath_crusade.abilities.data.AbilityDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds data-driven ability definitions and per-instance mutable state.
 */
public class AbilityInstanceComponent implements Component {
    public final List<AbilityDefinition> definitions = new ArrayList<>();
    public final Map<String, Map<String, Object>> state = new HashMap<>();

    public void addAbility(AbilityDefinition def) {
        definitions.add(def);
        if (def.state() != null && !def.state().isEmpty()) {
            state.put(def.id(), new HashMap<>(def.state()));
        } else {
            state.put(def.id(), new HashMap<>());
        }
    }
}
