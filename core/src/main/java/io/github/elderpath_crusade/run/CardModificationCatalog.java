package io.github.elderpath_crusade.run;

import java.util.List;
import java.util.Map;

/**
 * A small fixed catalog of card-modification rewards (CONTENT-002, Phase 3.6) — pure stat
 * deltas, no added abilities, so unlike relics these need no yaml content and work fully in
 * any environment (including this sandbox). {@code ELITE-001}'s reward room offers from this
 * pool when the roll (or the relic-pool-empty fallback) picks a modification over a relic.
 */
public final class CardModificationCatalog {
    public record Entry(String name, RunCardModification delta) {}

    private static final List<Entry> ENTRIES = List.of(
            new Entry("Sharpen (+1 Damage)", new RunCardModification(Map.of("damage", 1), List.of())),
            new Entry("Fortify (+2 Health)", new RunCardModification(Map.of("health", 2), List.of())),
            new Entry("Haste (+1 Speed)", new RunCardModification(Map.of("speed", 1), List.of())),
            new Entry("Discount (-1 Cost)", new RunCardModification(Map.of("cost", -1), List.of()))
    );

    private CardModificationCatalog() {}

    public static List<Entry> all() {
        return ENTRIES;
    }
}
