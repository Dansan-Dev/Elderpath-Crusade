package io.github.forest_of_dreams.abilities.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stores all StatsModifiers currently affecting a MonsterGamePiece.
 * Abilities add/remove their modifiers here based on triggers and conditions.
 */
public final class StatsAccumulator {
    private final List<StatsModifier> modifiers = new ArrayList<>();

    public void add(StatsModifier mod) {
        if (mod == null || mod.isNoOp()) return;
        if (!modifiers.contains(mod)) {
            modifiers.add(mod);
            mod._registerHolder(this);
        }
    }

    public void remove(StatsModifier mod) {
        if (mod == null) return;
        if (modifiers.remove(mod)) {
            mod._unregisterHolder(this);
        }
    }

    public void removeBySource(Object source) {
        if (source == null) return;
        // Remove all modifiers whose source == source
        for (int i = modifiers.size() - 1; i >= 0; i--) {
            StatsModifier m = modifiers.get(i);
            if (m.source == source) {
                modifiers.remove(i);
                m._unregisterHolder(this);
            }
        }
    }

    public List<StatsModifier> getAll() {
        return Collections.unmodifiableList(modifiers);
    }
}
