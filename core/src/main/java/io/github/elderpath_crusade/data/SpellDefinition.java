package io.github.elderpath_crusade.data;

import io.github.elderpath_crusade.abilities.data.Condition;
import io.github.elderpath_crusade.abilities.data.EffectNode;

import java.util.List;

/**
 * A data-driven spell loaded from spells.yaml. {@code targeting} is null for a
 * no-target self-cast spell (effects run immediately on click); otherwise the
 * spell requires clicking an occupied plot whose entity satisfies every
 * targeting condition (evaluated with the candidate entity populated as "$target").
 */
public record SpellDefinition(
    String id,
    String description,
    int manaCost,
    TargetingSpec targeting,
    List<EffectNode> effects
) {
    public record TargetingSpec(List<Condition> conditions) {}
}
