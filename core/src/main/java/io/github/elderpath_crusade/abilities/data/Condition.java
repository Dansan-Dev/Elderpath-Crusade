package io.github.elderpath_crusade.abilities.data;

import java.util.Map;

public record Condition(String type, Map<String, Object> params) {}
