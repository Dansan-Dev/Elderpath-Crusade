package io.github.elderpath_crusade.data;

import io.github.elderpath_crusade.abilities.data.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared SnakeYAML-map-to-record parsing for the ability effect DSL
 * (reactions, actions, modifiers, costs, selectors, effects, conditions).
 * Used by both AbilityRegistry (abilities.yaml) and SpellRegistry (spells.yaml)
 * so the two data files share one schema and one parser.
 */
public final class AbilityDataParsing {
    private AbilityDataParsing() {}

    @SuppressWarnings("unchecked")
    public static List<Reaction> parseReactions(Object obj) {
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
    public static List<ActionDef> parseActions(Object obj) {
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
    public static List<ModifierDef> parseModifiers(Object obj) {
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

    public static List<Cost> parseCosts(Object obj) {
        if (!(obj instanceof List<?> list)) return List.of();
        List<Cost> costs = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            costs.add(new Cost(map.get("type").toString(), ((Number) map.get("amount")).intValue()));
        }
        return costs;
    }

    @SuppressWarnings("unchecked")
    public static TargetSelector parseSelector(Object obj) {
        if (obj instanceof String s) return new TargetSelector(s);
        if (!(obj instanceof Map<?, ?> map)) return new TargetSelector("Self");
        String type = map.containsKey("type") ? map.get("type").toString() : "Self";
        Map<String, Object> params = map.containsKey("params") ? (Map<String, Object>) map.get("params") : Map.of();
        return new TargetSelector(type, params);
    }

    @SuppressWarnings("unchecked")
    public static List<EffectNode> parseEffects(Object obj) {
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
    public static List<Condition> parseConditions(Object obj) {
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
