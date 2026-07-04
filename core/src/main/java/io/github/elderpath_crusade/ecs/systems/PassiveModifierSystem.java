package io.github.elderpath_crusade.ecs.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import io.github.elderpath_crusade.abilities.data.AbilityDefinition;
import io.github.elderpath_crusade.abilities.data.ModifierDef;
import io.github.elderpath_crusade.abilities.data.TargetSelector;
import io.github.elderpath_crusade.abilities.stats.StatsModifier;
import io.github.elderpath_crusade.ecs.components.AbilityInstanceComponent;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.IdentityComponent;
import io.github.elderpath_crusade.ecs.components.ModifierComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TurnStartedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reads passive modifier definitions from each entity's AbilityInstanceComponent
 * and applies StatsModifier instances to target entities' ModifierComponent.accumulator.
 *
 * Tracks applied modifiers per source entity so they can be removed when targets
 * go out of range or the source entity leaves the family.
 *
 * Must run BEFORE ModifierResolutionSystem so computed stats are fresh each frame.
 */
public class PassiveModifierSystem extends EntitySystem {

    private Family family;
    private GridIndexSystem gridIndex;

    /**
     * Per-source-entity tracking: owner entity -> (abilityId#modifierIndex -> AuraEntry)
     */
    private final Map<Entity, Map<String, AuraEntry>> trackers = new IdentityHashMap<>();

    /**
     * Tracks which (owner,key) aura combinations have already bumped a given target's
     * remainingActions this turn, to prevent farming extra actions by repeatedly
     * leaving and re-entering aura range within the same turn.
     */
    private final Map<Entity, Set<String>> actionsBumpedThisTurn = new IdentityHashMap<>();

    private final Consumer<TurnStartedEvent> onTurnStarted = this::handleTurnStarted;

    private static class AuraEntry {
        final StatsModifier modifier;
        final Set<Entity> appliedTo = new HashSet<>();

        AuraEntry(StatsModifier modifier) {
            this.modifier = modifier;
        }
    }

    @Override
    public void addedToEngine(Engine engine) {
        family = Family.all(
            AbilityInstanceComponent.class,
            ModifierComponent.class,
            PositionComponent.class,
            AlignmentComponent.class
        ).get();
        gridIndex = engine.getSystem(GridIndexSystem.class);
        TypedEventBus.get().register(TurnStartedEvent.class, onTurnStarted);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        TypedEventBus.get().unregister(TurnStartedEvent.class, onTurnStarted);
    }

    private void handleTurnStarted(TurnStartedEvent event) {
        actionsBumpedThisTurn.clear();
    }

    @Override
    public void update(float deltaTime) {
        ImmutableArray<Entity> entities = getEngine().getEntitiesFor(family);

        // Collect the current set of active owners for stale-tracker cleanup
        Set<Entity> activeOwners = new HashSet<>();
        for (int i = 0; i < entities.size(); i++) {
            activeOwners.add(entities.get(i));
        }

        // Remove trackers for entities no longer in the family
        Set<Entity> staleOwners = new HashSet<>();
        for (Entity owner : trackers.keySet()) {
            if (!activeOwners.contains(owner)) {
                staleOwners.add(owner);
            }
        }
        for (Entity owner : staleOwners) {
            Map<String, AuraEntry> ownerEntries = trackers.remove(owner);
            if (ownerEntries != null) {
                for (AuraEntry entry : ownerEntries.values()) {
                    entry.modifier.clear();
                }
            }
        }

        // Process each active owner
        for (int i = 0; i < entities.size(); i++) {
            Entity owner = entities.get(i);
            AbilityInstanceComponent aic = owner.getComponent(AbilityInstanceComponent.class);
            PositionComponent pos = owner.getComponent(PositionComponent.class);
            AlignmentComponent alignment = owner.getComponent(AlignmentComponent.class);

            Map<String, AuraEntry> ownerEntries = trackers.computeIfAbsent(owner, k -> new HashMap<>());

            for (AbilityDefinition def : aic.definitions) {
                List<ModifierDef> modifiers = def.modifiers();
                if (modifiers == null || modifiers.isEmpty()) continue;

                for (int idx = 0; idx < modifiers.size(); idx++) {
                    ModifierDef modDef = modifiers.get(idx);
                    String key = def.id() + "#" + idx;

                    // Get or create a stable AuraEntry for this key
                    AuraEntry entry = ownerEntries.get(key);
                    if (entry == null) {
                        StatsModifier mod = buildModifier(modDef.stats());
                        entry = new AuraEntry(mod);
                        ownerEntries.put(key, entry);
                    }

                    // Resolve new target entities
                    Set<Entity> newTargets = resolveTargets(modDef.target(), owner, pos, alignment);

                    // Remove modifier from entities no longer in range
                    Set<Entity> toRemove = new HashSet<>(entry.appliedTo);
                    toRemove.removeAll(newTargets);
                    for (Entity target : toRemove) {
                        ModifierComponent mc = target.getComponent(ModifierComponent.class);
                        if (mc != null) {
                            mc.accumulator.remove(entry.modifier);
                        }
                    }

                    // Add modifier to newly in-range entities
                    Set<Entity> toAdd = new HashSet<>(newTargets);
                    toAdd.removeAll(entry.appliedTo);
                    for (Entity target : toAdd) {
                        ModifierComponent mc = target.getComponent(ModifierComponent.class);
                        if (mc != null) {
                            mc.accumulator.add(entry.modifier);
                        }

                        StatsComponent targetStats = target.getComponent(StatsComponent.class);
                        if (targetStats != null) {
                            if (entry.modifier.addMaxHealth > 0) {
                                targetStats.currentHealth += entry.modifier.addMaxHealth;
                            }
                            if (entry.modifier.addActions > 0) {
                                Set<String> bumped = actionsBumpedThisTurn.computeIfAbsent(target, k -> new HashSet<>());
                                String bumpKey = System.identityHashCode(owner) + ":" + key;
                                if (bumped.add(bumpKey)) {
                                    targetStats.remainingActions += entry.modifier.addActions;
                                }
                            }
                        }
                    }

                    // Update the applied-to set
                    entry.appliedTo.clear();
                    entry.appliedTo.addAll(newTargets);
                }
            }
        }
    }

    /**
     * Resolves the set of target entities for a given TargetSelector.
     */
    private Set<Entity> resolveTargets(TargetSelector selector, Entity owner,
                                        PositionComponent pos, AlignmentComponent ownerAlignment) {
        if (selector == null) return Set.of();

        switch (selector.type()) {
            case "Self":
                return Set.of(owner);

            case "AdjacentFriendlyUnits": {
                String unitName = getUnitNameParam(selector);
                Set<Entity> result = new HashSet<>();
                for (Entity adj : getAdjacentEntities(pos.row, pos.col)) {
                    AlignmentComponent adjAlign = adj.getComponent(AlignmentComponent.class);
                    if (adjAlign == null) continue;
                    if (adjAlign.alignment != ownerAlignment.alignment) continue;
                    if (adj == owner) continue;
                    if (unitName != null) {
                        IdentityComponent identity = adj.getComponent(IdentityComponent.class);
                        if (identity == null || !unitName.equals(identity.name)) continue;
                    }
                    result.add(adj);
                }
                return result;
            }

            case "AdjacentEnemies": {
                Set<Entity> result = new HashSet<>();
                for (Entity adj : getAdjacentEntities(pos.row, pos.col)) {
                    AlignmentComponent adjAlign = adj.getComponent(AlignmentComponent.class);
                    if (adjAlign == null) continue;
                    if (adjAlign.alignment == ownerAlignment.alignment) continue;
                    result.add(adj);
                }
                return result;
            }

            case "AllFriendlyUnits": {
                Set<Entity> result = new HashSet<>();
                ImmutableArray<Entity> all = getEngine().getEntitiesFor(
                    Family.all(AlignmentComponent.class).get());
                for (int i = 0; i < all.size(); i++) {
                    Entity e = all.get(i);
                    if (e == owner) continue;
                    AlignmentComponent align = e.getComponent(AlignmentComponent.class);
                    if (align == null || align.alignment != ownerAlignment.alignment) continue;
                    result.add(e);
                }
                return result;
            }

            default:
                return Set.of();
        }
    }

    /**
     * Returns all entities adjacent (N/E/S/W) to the given grid position.
     */
    private List<Entity> getAdjacentEntities(int row, int col) {
        if (gridIndex == null) return List.of();
        List<Entity> result = new java.util.ArrayList<>(4);
        int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] offset : offsets) {
            Entity e = gridIndex.getEntityAt(row + offset[0], col + offset[1]);
            if (e != null) result.add(e);
        }
        return result;
    }

    /**
     * Extracts the optional "unitName" param from a TargetSelector.
     */
    private static String getUnitNameParam(TargetSelector selector) {
        if (selector.params() == null) return null;
        Object val = selector.params().get("unitName");
        return val instanceof String ? (String) val : null;
    }

    /**
     * Builds a StatsModifier from the stats map in a ModifierDef.
     * The returned instance is reused across frames — do not create per frame.
     */
    private static StatsModifier buildModifier(Map<String, Object> stats) {
        StatsModifier mod = new StatsModifier();
        if (stats == null) return mod;
        if (stats.containsKey("addDamage"))
            mod.addDamage = ((Number) stats.get("addDamage")).intValue();
        if (stats.containsKey("addSpeed"))
            mod.addSpeed = ((Number) stats.get("addSpeed")).intValue();
        if (stats.containsKey("addActions"))
            mod.addActions = ((Number) stats.get("addActions")).intValue();
        if (stats.containsKey("addMaxHealth"))
            mod.addMaxHealth = ((Number) stats.get("addMaxHealth")).intValue();
        if (stats.containsKey("addRange"))
            mod.addRange = ((Number) stats.get("addRange")).intValue();
        if (stats.containsKey("addCost"))
            mod.addCost = ((Number) stats.get("addCost")).intValue();
        if (stats.containsKey("ignoreFriendlyUnitsAsBlockers"))
            mod.ignoreFriendlyUnitsAsBlockers = (Boolean) stats.get("ignoreFriendlyUnitsAsBlockers");
        if (stats.containsKey("ignoreHostileUnitsAsBlockers"))
            mod.ignoreHostileUnitsAsBlockers = (Boolean) stats.get("ignoreHostileUnitsAsBlockers");
        if (stats.containsKey("ignoreTerrainAsBlockers"))
            mod.ignoreTerrainAsBlockers = (Boolean) stats.get("ignoreTerrainAsBlockers");
        return mod;
    }
}
