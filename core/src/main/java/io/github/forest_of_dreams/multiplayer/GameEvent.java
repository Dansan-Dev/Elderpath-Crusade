package io.github.forest_of_dreams.multiplayer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GameEvent {
    private final GameEventType type;
    private final Map<String, Object> data;

    public GameEvent(GameEventType type, Map<String, Object> data) {
        this.type = type;
        this.data = (data == null) ? new HashMap<>() : new HashMap<>(data);
    }

    public GameEventType getType() { return type; }
    public Map<String, Object> getData() { return Collections.unmodifiableMap(data); }
}
