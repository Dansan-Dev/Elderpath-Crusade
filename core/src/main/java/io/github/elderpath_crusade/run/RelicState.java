package io.github.elderpath_crusade.run;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * The run's currently active relics, tracked by id in pick order. Resolving an id to its actual
 * in-battle ability name(s) / non-battle hooks is {@code RelicRegistry}'s job (UPG-001) — this
 * class only tracks which ids are active for the run.
 */
public class RelicState {
    private final LinkedHashSet<String> activeIds = new LinkedHashSet<>();

    public RelicState() {
    }

    public RelicState(List<String> initial) {
        if (initial != null) activeIds.addAll(initial);
    }

    public List<String> activeIds() {
        return List.copyOf(activeIds);
    }

    public boolean isActive(String relicId) {
        return activeIds.contains(relicId);
    }

    /** Adds a relic; no-op if already active (a relic can't be picked twice). */
    public void add(String relicId) {
        if (relicId == null || relicId.isBlank()) throw new IllegalArgumentException("relicId required");
        activeIds.add(relicId);
    }
}
