package io.github.elderpath_crusade.data;

import com.badlogic.gdx.Gdx;
import io.github.elderpath_crusade.run.RelicDefinition;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads RelicDefinitions from relics.yaml. Mirrors PieceRegistry/AbilityRegistry/SpellRegistry —
 * a relic's own id/description/battle-ability-names are curated data, hand-authored the same
 * way a piece's stats are; the ability names it references must already exist in
 * abilities.yaml (loaded separately by AbilityRegistry).
 *
 * <p>Expected shape:
 * <pre>
 * relics:
 *   PackLeader:
 *     description: "+1 attack to all your units"
 *     battleAbilities: [PackLeaderAura]
 * </pre>
 */
public final class RelicRegistry {
    private static final Map<String, RelicDefinition> REGISTRY = new LinkedHashMap<>();

    private RelicRegistry() {
    }

    public static void load() {
        REGISTRY.clear();
        if (Gdx.files == null) return; // test environment
        try {
            String text = Gdx.files.internal("data/relics.yaml").readString();
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(text);
            Object relicsObj = root.get("relics");
            if (!(relicsObj instanceof Map<?, ?> relics)) return;

            for (Map.Entry<?, ?> entry : relics.entrySet()) {
                String id = entry.getKey().toString();
                if (!(entry.getValue() instanceof Map<?, ?> defMap)) continue;
                REGISTRY.put(id, parseDefinition(id, defMap));
            }
        } catch (Exception e) {
            System.err.println("Failed to load relics.yaml: " + e.getMessage());
        }
    }

    /** Adds or replaces one definition — for tests and headless matches that define relics in code. */
    public static void register(RelicDefinition definition) {
        REGISTRY.put(definition.id(), definition);
    }

    public static RelicDefinition get(String id) {
        return REGISTRY.get(id);
    }

    public static void clear() {
        REGISTRY.clear();
    }

    private static RelicDefinition parseDefinition(String id, Map<?, ?> map) {
        String description = map.containsKey("description") ? map.get("description").toString() : "";
        List<String> battleAbilities = toStringList(map.get("battleAbilities"));
        return new RelicDefinition(id, description, battleAbilities);
    }

    private static List<String> toStringList(Object obj) {
        if (!(obj instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object o : list) out.add(String.valueOf(o));
        return out;
    }
}
