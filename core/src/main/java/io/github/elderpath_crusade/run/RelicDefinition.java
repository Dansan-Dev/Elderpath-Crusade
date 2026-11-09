package io.github.elderpath_crusade.run;

import java.util.List;

/**
 * One relic a run can offer — its id, description, the names of existing
 * {@code AbilityDefinition}s (from {@code abilities.yaml}, the same pipeline a piece's own
 * abilities use) its carrier piece should attach when active, and its non-battle
 * {@link RelicRunHook}s (UPG-002). A relic's in-battle half can carry any mix of passive aura,
 * triggered reaction, or player-activatable action, since an {@code AbilityDefinition} already
 * supports all three (see roguelike-run-plan.md's "Relic System"); its run-hook half runs
 * entirely client-side in {@code RunManager}, between battles.
 *
 * <p>{@code runHooks} is behavior, not data — it can only ever be populated by in-code
 * registration ({@code RelicRegistry.register}), never by {@code relics.yaml} ({@link
 * #battleOnly} is what {@code RelicRegistry}'s loader builds).
 */
public record RelicDefinition(String id, String description, List<String> battleAbilityNames, List<RelicRunHook> runHooks) {
    public RelicDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id required");
        if (description == null) description = "";
        battleAbilityNames = List.copyOf(battleAbilityNames);
        runHooks = List.copyOf(runHooks);
    }

    /** A relic with no non-battle effect — what a yaml-authored relic always is. */
    public static RelicDefinition battleOnly(String id, String description, List<String> battleAbilityNames) {
        return new RelicDefinition(id, description, battleAbilityNames, List.of());
    }

    public boolean hasBattleEffect() {
        return !battleAbilityNames.isEmpty();
    }

    public boolean hasRunHooks() {
        return !runHooks.isEmpty();
    }
}
