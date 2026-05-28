package io.github.elderpath_crusade.managers;

import io.github.elderpath_crusade.interfaces.Renderable;

import java.util.*;

/**
 * Central registry for Z-ordering of game Renderables.
 * Maintains per-Z buckets and a sorted set of active Z levels.
 */
public final class ZIndexRegistry {
    private ZIndexRegistry() {}

    private static final Map<Integer, Set<Renderable>> zBuckets = new HashMap<>();
    private static final NavigableSet<Integer> zLevels = new TreeSet<>();

    public static void add(Renderable r) {
        for (Integer z : r.getZs()) {
            zBuckets.computeIfAbsent(z, k -> new LinkedHashSet<>()).add(r);
            zLevels.add(z);
        }
    }

    public static void remove(Renderable r) {
        for (Integer z : r.getZs()) {
            Set<Renderable> bucket = zBuckets.get(z);
            if (bucket != null) {
                bucket.remove(r);
                if (bucket.isEmpty()) {
                    zBuckets.remove(z);
                    zLevels.remove(z);
                }
            }
        }
    }

    public static void clear() {
        zBuckets.clear();
        zLevels.clear();
    }

    public static void notifyZChanged(Renderable r) {
        // Remove from ALL buckets since r.getZs() may already return new values
        zBuckets.values().forEach(set -> set.remove(r));
        zBuckets.entrySet().removeIf(entry -> {
            if (entry.getValue().isEmpty()) {
                zLevels.remove(entry.getKey());
                return true;
            }
            return false;
        });
        add(r);
    }

    public static Iterable<Integer> getZLevels() {
        return zLevels;
    }

    public static Collection<Renderable> getBucket(int z) {
        return zBuckets.get(z);
    }
}
