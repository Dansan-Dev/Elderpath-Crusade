package io.github.elderpath_crusade.abilities.data;

import java.util.HashMap;
import java.util.Map;

public class ExpressionContext {
    private final Map<String, Object> variables = new HashMap<>();

    public void set(String path, Object value) { variables.put(path, value); }

    public Object get(String path) { return variables.get(path); }

    public ExpressionContext withState(Map<String, Object> state) {
        if (state != null) {
            state.forEach((k, v) -> set("$state." + k, v));
        }
        return this;
    }

    public ExpressionContext withEvent(String prefix, Map<String, Object> eventData) {
        if (eventData != null) {
            eventData.forEach((k, v) -> set("$" + prefix + "." + k, v));
        }
        return this;
    }

    public ExpressionContext withSelf(Map<String, Object> selfData) {
        if (selfData != null) {
            selfData.forEach((k, v) -> set("$self." + k, v));
        }
        return this;
    }

    public ExpressionContext withTarget(Map<String, Object> targetData) {
        if (targetData != null) {
            targetData.forEach((k, v) -> set("$target." + k, v));
        }
        return this;
    }
}
