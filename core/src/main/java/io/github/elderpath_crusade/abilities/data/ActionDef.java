package io.github.elderpath_crusade.abilities.data;

import java.util.List;

public record ActionDef(
    List<Cost> costs,
    TargetSelector targetSelector,
    List<EffectNode> effects
) {}
