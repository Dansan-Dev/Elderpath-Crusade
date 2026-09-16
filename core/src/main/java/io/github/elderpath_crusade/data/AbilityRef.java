package io.github.elderpath_crusade.data;

import java.util.Map;

/**
 * A piece's reference to an ability by name, with optional per-instance stat
 * overrides (e.g. {name: "RangeBonus", params: {addRange: 4}}).
 */
public record AbilityRef(String name, Map<String, Object> params) {
    public AbilityRef(String name) { this(name, Map.of()); }
}
