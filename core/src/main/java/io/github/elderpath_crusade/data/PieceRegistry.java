package io.github.elderpath_crusade.data;

import com.badlogic.gdx.Gdx;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

public final class PieceRegistry {
    private static final Map<String, PieceDefinition> PIECES = new LinkedHashMap<>();

    private PieceRegistry() {}

    @SuppressWarnings("unchecked")
    public static void load() {
        PIECES.clear();
        String text = Gdx.files.internal("data/pieces.yaml").readString();
        Map<String, Object> root = new Yaml().load(text);
        Map<String, Map<String, Object>> entries = (Map<String, Map<String, Object>>) root.get("pieces");
        for (Map.Entry<String, Map<String, Object>> entry : entries.entrySet()) {
            String name = entry.getKey();
            Map<String, Object> v = entry.getValue();
            List<String> abilities = v.containsKey("abilities")
                    ? ((List<?>) v.get("abilities")).stream().map(Object::toString).toList()
                    : List.of();
            PIECES.put(name, new PieceDefinition(
                    name,
                    (int) v.get("cost"),
                    (int) v.get("health"),
                    (int) v.get("damage"),
                    (int) v.get("speed"),
                    (int) v.get("actions"),
                    abilities
            ));
        }
    }

    public static PieceDefinition get(String name) {
        return PIECES.get(name);
    }

    public static Collection<String> getAllNames() {
        return Collections.unmodifiableSet(PIECES.keySet());
    }
}
