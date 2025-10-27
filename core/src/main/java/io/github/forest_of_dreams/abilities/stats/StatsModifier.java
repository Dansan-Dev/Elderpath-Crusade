package io.github.forest_of_dreams.abilities.stats;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a composable modifier for piece stats. Use additive first, then multiplicative.
 * Multipliers are applied as (base + add) * (1 + mult).
 */
public final class StatsModifier {
    /** Optional reference to the source (e.g., the Ability instance) for easy removal. */
    public Object source;

    public int addCost;
    public int addMaxHealth;
    public int addDamage;
    public int addSpeed;
    public int addActions;

    public float multCost;
    public float multMaxHealth;
    public float multDamage;
    public float multSpeed;
    public float multActions;

    // Track which accumulators this modifier is currently applied to for fast removal.
    private final Set<StatsAccumulator> holders = new HashSet<>();

    public static StatsModifier none() { return new StatsModifier(); }

    public boolean isNoOp() {
        return addCost == 0 && addMaxHealth == 0 && addDamage == 0 && addSpeed == 0 && addActions == 0
            && multCost == 0 && multMaxHealth == 0 && multDamage == 0 && multSpeed == 0 && multActions == 0;
    }

    public static int applyInt(int base, int add, float mult) {
        float v = (base + add) * (1f + mult);
        return Math.max(0, Math.round(v));
    }

    // Internal hooks called by StatsAccumulator when this modifier is added/removed.
    void _registerHolder(StatsAccumulator acc) { if (acc != null) holders.add(acc); }
    void _unregisterHolder(StatsAccumulator acc) { if (acc != null) holders.remove(acc); }

    /** Remove this modifier from all accumulators it is present in. */
    public void clear() {
        if (holders.isEmpty()) return;
        // Copy to avoid concurrent modification
        Set<StatsAccumulator> snapshot = new HashSet<>(holders);
        for (StatsAccumulator acc : snapshot) {
            acc.remove(this);
        }
        holders.clear();
    }
}
