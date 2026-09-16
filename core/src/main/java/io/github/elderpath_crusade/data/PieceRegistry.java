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
            List<AbilityRef> abilities = v.containsKey("abilities")
                    ? parseAbilities((List<?>) v.get("abilities"))
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

    @SuppressWarnings("unchecked")
    private static List<AbilityRef> parseAbilities(List<?> raw) {
        List<AbilityRef> abilities = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof Map<?, ?> m) {
                String name = (String) m.get("name");
                Map<String, Object> params = m.containsKey("params")
                        ? (Map<String, Object>) m.get("params") : Map.of();
                abilities.add(new AbilityRef(name, params));
            } else {
                abilities.add(new AbilityRef(item.toString()));
            }
        }
        return abilities;
    }

    public static String toRegistryKey(String displayName) {
        return displayName.replace(" ", "");
    }

    public static PieceDefinition get(String name) {
        return PIECES.get(name);
    }

    public static Collection<String> getAllNames() {
        return Collections.unmodifiableSet(PIECES.keySet());
    }
}
