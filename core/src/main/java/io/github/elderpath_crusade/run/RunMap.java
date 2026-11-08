package io.github.elderpath_crusade.run;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A run's branching node graph — nodes plus which nodes are reachable from which, like Slay the
 * Spire's map. The real generator (seeded, branching, converging paths — MAP-001) doesn't exist
 * yet; {@link #linearChain(int)} is a placeholder that unblocks RunState/RunManager until it
 * does.
 */
public record RunMap(List<RunNode> nodes, int startNodeId) {
    public RunMap {
        if (nodes.isEmpty()) throw new IllegalArgumentException("a map needs at least one node");
        nodes = List.copyOf(nodes);
        Map<Integer, RunNode> byId = new LinkedHashMap<>();
        for (RunNode node : nodes) {
            if (byId.put(node.id(), node) != null) {
                throw new IllegalArgumentException("duplicate node id " + node.id());
            }
        }
        if (!byId.containsKey(startNodeId)) {
            throw new IllegalArgumentException("startNodeId " + startNodeId + " is not one of the map's nodes");
        }
        for (RunNode node : nodes) {
            for (int nextId : node.next()) {
                if (!byId.containsKey(nextId)) {
                    throw new IllegalArgumentException("node " + node.id() + " points at unknown node " + nextId);
                }
            }
        }
    }

    public RunNode node(int id) {
        for (RunNode node : nodes) {
            if (node.id() == id) return node;
        }
        throw new IllegalArgumentException("no node with id " + id);
    }

    public List<RunNode> reachableFrom(int id) {
        List<RunNode> out = new ArrayList<>();
        for (int nextId : node(id).next()) out.add(node(nextId));
        return out;
    }

    /**
     * A straight chain of {@code totalNodes - 1} Battle nodes followed by one terminal Boss
     * node, always starting at node 0. Placeholder map shape only — no Elite/Rest nodes, no
     * branching.
     */
    public static RunMap linearChain(int totalNodes) {
        if (totalNodes < 1) throw new IllegalArgumentException("totalNodes must be >= 1");
        List<RunNode> nodes = new ArrayList<>();
        for (int i = 0; i < totalNodes; i++) {
            boolean last = i == totalNodes - 1;
            RunNodeType type = last ? RunNodeType.BOSS : RunNodeType.BATTLE;
            List<Integer> next = last ? List.of() : List.of(i + 1);
            nodes.add(new RunNode(i, type, next));
        }
        return new RunMap(nodes, 0);
    }
}
