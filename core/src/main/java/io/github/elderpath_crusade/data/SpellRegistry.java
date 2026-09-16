package io.github.elderpath_crusade.data;

import com.badlogic.gdx.Gdx;
import io.github.elderpath_crusade.abilities.data.Condition;
import io.github.elderpath_crusade.abilities.data.EffectNode;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

/**
 * Loads SpellDefinitions from spells.yaml. Mirrors PieceRegistry/AbilityRegistry.
 */
public final class SpellRegistry {
    private static final Map<String, SpellDefinition> REGISTRY = new LinkedHashMap<>();

    private SpellRegistry() {}

    public static void load() {
        REGISTRY.clear();
        if (Gdx.files == null) return; // test environment
        try {
            String text = Gdx.files.internal("data/spells.yaml").readString();
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(text);
            Object spellsObj = root.get("spells");
            if (!(spellsObj instanceof Map<?, ?> spells)) return;

            for (Map.Entry<?, ?> entry : spells.entrySet()) {
                String name = entry.getKey().toString();
                if (!(entry.getValue() instanceof Map<?, ?> defMap)) continue;
                REGISTRY.put(name, parseDefinition(name, defMap));
            }
        } catch (Exception e) {
            System.err.println("Failed to load spells.yaml: " + e.getMessage());
        }
    }

    public static SpellDefinition get(String name) {
        return REGISTRY.get(name);
    }

    public static Collection<String> getAllNames() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }

    @SuppressWarnings("unchecked")
    private static SpellDefinition parseDefinition(String name, Map<?, ?> map) {
        String description = map.containsKey("description") ? map.get("description").toString() : "";
        int manaCost = map.containsKey("manaCost") ? ((Number) map.get("manaCost")).intValue() : 0;
        SpellDefinition.TargetingSpec targeting = parseTargeting(map.get("targeting"));
        List<EffectNode> effects = AbilityDataParsing.parseEffects(map.get("effects"));
        return new SpellDefinition(name, description, manaCost, targeting, effects);
    }

    @SuppressWarnings("unchecked")
    private static SpellDefinition.TargetingSpec parseTargeting(Object obj) {
        if (!(obj instanceof Map<?, ?> map)) return null;
        List<Condition> conditions = AbilityDataParsing.parseConditions(map.get("conditions"));
        return new SpellDefinition.TargetingSpec(conditions);
    }
}
