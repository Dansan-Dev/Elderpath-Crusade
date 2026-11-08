package io.github.elderpath_crusade.run;

import java.util.List;

/**
 * One node in a run's map — its type and which other node ids are reachable from it (empty for
 * a terminal node, i.e. the map's Boss). Pure data; RunMap owns the graph as a whole.
 */
public record RunNode(int id, RunNodeType type, List<Integer> next) {
    public RunNode {
        if (id < 0) throw new IllegalArgumentException("id must be >= 0");
        if (type == null) throw new IllegalArgumentException("type required");
        next = List.copyOf(next);
    }

    public boolean isTerminal() {
        return next.isEmpty();
    }
}
