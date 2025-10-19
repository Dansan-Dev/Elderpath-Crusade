package io.github.forest_of_dreams.managers;

import io.github.forest_of_dreams.interfaces.Renderable;

import java.util.*;

/**
 * Central registry for Z-ordering of game Renderables.
 * Maintains per-Z buckets and a sorted set of active Z levels.
 */
public final class ZIndexRegistry {
    private ZIndexRegistry() {}

    private static final Map<Integer, List<Renderable>> zBuckets = new HashMap<>();
    private static final NavigableSet<Integer> zLevels = new TreeSet<>();

    public static void add(Renderable r) {
        for (Integer z : r.getZs()) {
            zBuckets.computeIfAbsent(z, k -> new ArrayList<>()).add(r);
            zLevels.add(z);
        }
    }

    public static void remove(Renderable r) {
        for (Integer z : r.getZs()) {
            List<Renderable> list = zBuckets.get(z);
            if (list != null) {
                list.remove(r);
                if (list.isEmpty()) {
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
        remove(r);
        add(r);
    }

    public static Iterable<Integer> getZLevels() {
        return zLevels;
    }

    public static List<Renderable> getBucket(int z) {
        return zBuckets.get(z);
    }
}
