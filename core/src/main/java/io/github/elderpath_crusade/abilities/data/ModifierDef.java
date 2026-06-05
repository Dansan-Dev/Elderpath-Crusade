package io.github.elderpath_crusade.abilities.data;

import java.util.Map;

public record ModifierDef(
    TargetSelector target,
    Map<String, Object> stats
) {}
