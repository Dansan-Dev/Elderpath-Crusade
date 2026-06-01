package io.github.elderpath_crusade.ecs.components;

import com.badlogic.ashley.core.Component;
import io.github.elderpath_crusade.abilities.Ability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds ability instances for an entity. This is the authoritative registry
 * for abilities — MonsterGamePiece delegates to this component when linked.
 */
public class AbilityComponent implements Component {
    public final List<String> abilityNames = new ArrayList<>();
    private final List<Ability> abilities = new ArrayList<>();

    public AbilityComponent add(String abilityName) {
        abilityNames.add(abilityName);
        return this;
    }

    public void addAbility(Ability ability) {
        if (ability == null) return;
        abilities.add(ability);
        abilityNames.add(ability.getName());
    }

    public boolean removeAbility(Ability ability) {
        if (ability == null) return false;
        boolean removed = abilities.remove(ability);
        if (removed) {
            abilityNames.remove(ability.getName());
        }
        return removed;
    }

    public List<Ability> getAbilities() {
        return Collections.unmodifiableList(abilities);
    }

    public void clear() {
        abilities.clear();
        abilityNames.clear();
    }
}
