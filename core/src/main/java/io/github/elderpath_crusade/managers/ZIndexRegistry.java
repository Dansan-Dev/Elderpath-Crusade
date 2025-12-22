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
        zBuckets.values().forEach(set -> set.remove(r));
        // Cleanup empty buckets and levels
        Iterator<Map.Entry<Integer, Set<Renderable>>> it = zBuckets.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Set<Renderable>> entry = it.next();
            if (entry.getValue().isEmpty()) {
                zLevels.remove(entry.getKey());
                it.remove();
            }
        }
    }

    public static void clear() {
        zBuckets.clear();
        zLevels.clear();
    }

    public static void notifyZChanged(Renderable r) {
        // Collect old Zs before removing to ensure cleanup
        // Note: remove(r) currently uses r.getZs() which might have already changed.
        // This is a known issue if getZs() is dynamic.
        // However, Board.markDirtyAndNotify sets zsDirty = true, so getZs() will return NEW Zs.
        // If we want to remove from OLD Zs, we'd need to track them.
        // But since ZIndexRegistry.remove iterates over ALL relevant buckets, it should be fine
        // as long as we know WHICH buckets it was in.

        // Actually, ZIndexRegistry.remove(r) iterates over r.getZs().
        // If r.getZs() changed, remove(r) will MISS the old Z-buckets.

        // I should probably fix ZIndexRegistry.remove to be more robust,
        // OR Board should notify BEFORE changing Zs.

        remove(r);
        add(r);
    }

    public static Iterable<Integer> getZLevels() {
        return zLevels;
    }

    public static Collection<Renderable> getBucket(int z) {
        return zBuckets.get(z);
    }
}
