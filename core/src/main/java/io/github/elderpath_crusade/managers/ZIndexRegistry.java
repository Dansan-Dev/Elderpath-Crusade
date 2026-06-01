package io.github.elderpath_crusade.managers;

import io.github.elderpath_crusade.interfaces.Renderable;

import java.util.*;

public class ZIndexRegistry {
    private final Map<Integer, Set<Renderable>> zBuckets = new HashMap<>();
    private final NavigableSet<Integer> zLevels = new TreeSet<>();

    public ZIndexRegistry() {}

    public void add(Renderable r) {
        for (Integer z : r.getZs()) {
            zBuckets.computeIfAbsent(z, k -> new LinkedHashSet<>()).add(r);
            zLevels.add(z);
        }
    }

    public void remove(Renderable r) {
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

    public void clear() {
        zBuckets.clear();
        zLevels.clear();
    }

    public void notifyZChanged(Renderable r) {
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

    public Iterable<Integer> getZLevels() {
        return zLevels;
    }

    public Collection<Renderable> getBucket(int z) {
        return zBuckets.get(z);
    }
}
