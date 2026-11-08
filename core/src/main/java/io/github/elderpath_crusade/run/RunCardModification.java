package io.github.elderpath_crusade.run;

import java.util.List;
import java.util.Map;

/**
 * A modification applied to one specific {@link RunCardInstance} — stat deltas (additive, same
 * stat-name keys as a piece's base stats, e.g. "damage", "health") and/or extra ability names
 * granted on top of the base piece's own. Applying this at summon time (a synthetic per-run
 * PieceDefinition/registry key) is UPG-003's job — this record only holds the data.
 */
public record RunCardModification(Map<String, Integer> statDeltas, List<String> addedAbilities) {
    public static final RunCardModification NONE = new RunCardModification(Map.of(), List.of());

    public RunCardModification {
        statDeltas = Map.copyOf(statDeltas);
        addedAbilities = List.copyOf(addedAbilities);
    }

    public boolean isEmpty() {
        return statDeltas.isEmpty() && addedAbilities.isEmpty();
    }
}
