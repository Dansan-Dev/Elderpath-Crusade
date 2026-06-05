package io.github.elderpath_crusade.abilities.data;

import java.util.List;
import java.util.Map;

public record AbilityDefinition(
    String id,
    String description,
    Map<String, Object> state,
    List<Reaction> reactions,
    List<ActionDef> actions,
    List<ModifierDef> modifiers
) {}
