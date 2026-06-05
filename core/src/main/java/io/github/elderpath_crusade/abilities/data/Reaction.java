package io.github.elderpath_crusade.abilities.data;

import java.util.List;

public record Reaction(
    TriggerType trigger,
    List<Condition> conditions,
    List<EffectNode> effects
) {}
