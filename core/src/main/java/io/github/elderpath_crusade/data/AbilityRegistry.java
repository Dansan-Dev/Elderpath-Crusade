package io.github.elderpath_crusade.data;

import io.github.elderpath_crusade.abilities.data.*;
import com.badlogic.gdx.Gdx;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

/**
 * Loads AbilityDefinitions from abilities.yaml.
 * Maps ability names to their data-driven definitions.
 */
public final class AbilityRegistry {
    private static final Map<String, AbilityDefinition> REGISTRY = new HashMap<>();

    private AbilityRegistry() {}

    public static void load() {
        REGISTRY.clear();
        if (Gdx.files == null) return; // test environment
        try {
            String text = Gdx.files.internal("data/abilities.yaml").readString();
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(text);
            Object abilitiesObj = root.get("abilities");
            if (!(abilitiesObj instanceof Map<?, ?> abilities)) return;

            for (Map.Entry<?, ?> entry : abilities.entrySet()) {
                String name = entry.getKey().toString();
                if (!(entry.getValue() instanceof Map<?, ?> defMap)) continue;
                AbilityDefinition def = parseDefinition(name, defMap);
                REGISTRY.put(name, def);
            }
        } catch (Exception e) {
            System.err.println("Failed to load abilities.yaml: " + e.getMessage());
        }
    }

    public static AbilityDefinition get(String name) {
        return REGISTRY.get(name);
    }

    public static boolean has(String name) {
        return REGISTRY.containsKey(name);
    }

    /**
     * Looks up an ability by name and builds a per-piece specialized copy from {@code params}.
     */
    public static AbilityDefinition specialize(String name, Map<String, Object> params) {
        return applyParams(REGISTRY.get(name), params);
    }

    /**
     * Builds a per-piece copy of a base ability with its modifier stats overridden by
     * {@code params} (e.g. RangeBonus + {addRange: 4}) and its description's
     * "{key}" placeholders substituted with the corresponding param values.
     */
    public static AbilityDefinition applyParams(AbilityDefinition base, Map<String, Object> params) {
        if (base == null || params == null || params.isEmpty()) return base;

        List<ModifierDef> modifiers = base.modifiers();
        if (modifiers != null && !modifiers.isEmpty()) {
            List<ModifierDef> specialized = new ArrayList<>();
            for (ModifierDef md : modifiers) {
                Map<String, Object> mergedStats = new HashMap<>(md.stats());
                mergedStats.putAll(params);
                specialized.add(new ModifierDef(md.target(), mergedStats));
            }
            modifiers = specialized;
        }

        String description = base.description();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            description = description.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
        }

        return new AbilityDefinition(base.id(), description, base.state(), base.reactions(), base.actions(), modifiers);
    }

    @SuppressWarnings("unchecked")
    private static AbilityDefinition parseDefinition(String name, Map<?, ?> map) {
        String description = map.containsKey("description") ? map.get("description").toString() : "";
        Map<String, Object> state = map.containsKey("state") ? (Map<String, Object>) map.get("state") : Map.of();
        List<Reaction> reactions = parseReactions(map.get("reactions"));
        List<ActionDef> actions = parseActions(map.get("actions"));
        List<ModifierDef> modifiers = parseModifiers(map.get("modifiers"));
        return new AbilityDefinition(name, description, state, reactions, actions, modifiers);
    }

    @SuppressWarnings("unchecked")
    private static List<Reaction> parseReactions(Object obj) {
        if (!(obj instanceof List<?> list)) return List.of();
        List<Reaction> reactions = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            TriggerType trigger = TriggerType.valueOf(map.get("trigger").toString());
            List<Condition> conditions = parseConditions(map.get("conditions"));
            List<EffectNode> effects = parseEffects(map.get("effects"));
            reactions.add(new Reaction(trigger, conditions, effects));
        }
        return reactions;
    }

    @SuppressWarnings("unchecked")
    private static List<ActionDef> parseActions(Object obj) {
        if (!(obj instanceof List<?> list)) return List.of();
        List<ActionDef> actions = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            List<Cost> costs = parseCosts(map.get("costs"));
            TargetSelector selector = parseSelector(map.get("targetSelector"));
            List<EffectNode> effects = parseEffects(map.get("effects"));
            actions.add(new ActionDef(costs, selector, effects));
        }
        return actions;
    }

    @SuppressWarnings("unchecked")
    private static List<ModifierDef> parseModifiers(Object obj) {
        if (!(obj instanceof List<?> list)) return List.of();
        List<ModifierDef> modifiers = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            TargetSelector target = parseSelector(map.get("target"));
            Map<String, Object> stats = map.containsKey("stats") ? (Map<String, Object>) map.get("stats") : Map.of();
            modifiers.add(new ModifierDef(target, stats));
        }
        return modifiers;
    }

    @SuppressWarnings("unchecked")
    private static List<Cost> parseCosts(Object obj) {
        if (!(obj instanceof List<?> list)) return List.of();
        List<Cost> costs = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            costs.add(new Cost(map.get("type").toString(), ((Number) map.get("amount")).intValue()));
        }
        return costs;
    }

    @SuppressWarnings("unchecked")
    private static TargetSelector parseSelector(Object obj) {
        if (obj instanceof String s) return new TargetSelector(s);
        if (!(obj instanceof Map<?, ?> map)) return new TargetSelector("Self");
        String type = map.containsKey("type") ? map.get("type").toString() : "Self";
        Map<String, Object> params = map.containsKey("params") ? (Map<String, Object>) map.get("params") : Map.of();
        return new TargetSelector(type, params);
    }

    @SuppressWarnings("unchecked")
    private static List<EffectNode> parseEffects(Object obj) {
        if (!(obj instanceof List<?> list)) return List.of();
        List<EffectNode> effects = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            String type = map.containsKey("type") ? map.get("type").toString() : "";
            Map<String, Object> params = new HashMap<>((Map<String, Object>) map);
            params.remove("type");
            if (params.containsKey("params")) {
                params.putAll((Map<String, Object>) params.remove("params"));
            }
            effects.add(new EffectNode(type, params));
        }
        return effects;
    }

    @SuppressWarnings("unchecked")
    private static List<Condition> parseConditions(Object obj) {
        if (!(obj instanceof List<?> list)) return List.of();
        List<Condition> conditions = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            String type = map.containsKey("type") ? map.get("type").toString() : "Always";
            Map<String, Object> params = new HashMap<>((Map<String, Object>) map);
            params.remove("type");
            conditions.add(new Condition(type, params));
        }
        return conditions;
    }
}
