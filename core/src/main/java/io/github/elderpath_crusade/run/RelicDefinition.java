package io.github.elderpath_crusade.run;

import java.util.List;

/**
 * One relic a run can offer — its id, description, and the names of existing
 * {@code AbilityDefinition}s (from {@code abilities.yaml}, the same pipeline a piece's own
 * abilities use) its carrier piece should attach when active. A relic can carry any mix of
 * passive aura, triggered reaction, or player-activatable action, since an {@code
 * AbilityDefinition} already supports all three (see roguelike-run-plan.md's "Relic System").
 * Non-battle effects (run economy, the map, rest/recovery — UPG-002) are a separate concern,
 * not part of this record.
 */
public record RelicDefinition(String id, String description, List<String> battleAbilityNames) {
    public RelicDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id required");
        if (description == null) description = "";
        battleAbilityNames = List.copyOf(battleAbilityNames);
    }

    public boolean hasBattleEffect() {
        return !battleAbilityNames.isEmpty();
    }
}
