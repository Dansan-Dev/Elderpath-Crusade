package io.github.elderpath_crusade.abilities.data;

import java.util.Map;

public record TargetSelector(String type, Map<String, Object> params) {
    public TargetSelector(String type) { this(type, Map.of()); }
}
