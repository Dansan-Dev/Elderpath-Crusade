package io.github.elderpath_crusade.run;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * A run's branching node graph — nodes plus which nodes are reachable from which, like Slay the
 * Spire's map. {@link #generate(long)}/{@link #generateBranching(long, int, int)} (MAP-001) build
 * a real seeded, branching, converging map; {@link #linearChain(int)} stays as a simple,
 * deterministic fixture for tests that don't care about branching.
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
     * Every node's distance (in floors) from the start node, via BFS — 0 for the start itself.
     * Shared by DIFF-001's depth-scaling formula and MAP-002's floor-by-floor map layout, so
     * both agree on what "depth" means for any map shape.
     */
    public Map<Integer, Integer> depths() {
        Map<Integer, Integer> depthOf = new LinkedHashMap<>();
        Deque<Integer> queue = new ArrayDeque<>();
        depthOf.put(startNodeId, 0);
        queue.add(startNodeId);
        while (!queue.isEmpty()) {
            int id = queue.poll();
            int depth = depthOf.get(id);
            for (int nextId : node(id).next()) {
                if (!depthOf.containsKey(nextId)) {
                    depthOf.put(nextId, depth + 1);
                    queue.add(nextId);
                }
            }
        }
        return depthOf;
    }

    /** The given node's distance from the start node (see {@link #depths()}). */
    public int depthOf(int id) {
        Integer depth = depths().get(id);
        if (depth == null) throw new IllegalArgumentException("node " + id + " is unreachable from the start");
        return depth;
    }

    /**
     * A straight chain of {@code totalNodes - 1} Battle nodes followed by one terminal Boss
     * node, always starting at node 0. A simple, deterministic fixture — no Elite/Rest nodes,
     * no branching — for tests that need "a" valid map without caring about its shape.
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

    /** A reasonable default shape: 4 middle floors of 3 nodes each, between the start and the boss. */
    public static RunMap generate(long seed) {
        return generateBranching(seed, 4, 3);
    }

    /**
     * A seeded branching node graph: a single Battle start node, {@code middleFloors} floors of
     * {@code width} nodes each (type randomly Battle/Elite/Rest, weighted 60/20/20), converging
     * on a single terminal Boss node. Each node connects to 1-2 nodes in the next floor; every
     * node in a floor is guaranteed at least one incoming edge from the previous floor (nothing
     * is ever unreachable) and every non-Boss node at least one outgoing edge to the next
     * (nothing is ever a dead end before the boss). Guarantees at least one Elite node among the
     * middle floors (Phase 3.6) — the 60/20/20 weighting alone leaves a real chance of none at
     * all (~41% per floor), which would make a multi-floor run's Elite reward unreachable on an
     * unlucky seed; a map that already rolled one naturally is untouched.
     */
    public static RunMap generateBranching(long seed, int middleFloors, int width) {
        if (middleFloors < 1) throw new IllegalArgumentException("middleFloors must be >= 1");
        if (width < 1) throw new IllegalArgumentException("width must be >= 1");
        Random rng = new Random(seed);

        List<List<Integer>> floors = new ArrayList<>();
        Map<Integer, RunNodeType> types = new LinkedHashMap<>();
        int nextId = 0;

        floors.add(List.of(nextId));
        types.put(nextId, RunNodeType.BATTLE);
        nextId++;

        for (int f = 0; f < middleFloors; f++) {
            List<Integer> floor = new ArrayList<>();
            for (int i = 0; i < width; i++) {
                floor.add(nextId);
                types.put(nextId, randomMiddleNodeType(rng));
                nextId++;
            }
            floors.add(floor);
        }

        if (types.values().stream().noneMatch(t -> t == RunNodeType.ELITE)) {
            List<Integer> middleNodeIds = new ArrayList<>();
            for (int f = 1; f < floors.size(); f++) middleNodeIds.addAll(floors.get(f));
            int forced = middleNodeIds.get(rng.nextInt(middleNodeIds.size()));
            types.put(forced, RunNodeType.ELITE);
        }

        int bossId = nextId;
        types.put(bossId, RunNodeType.BOSS);
        floors.add(List.of(bossId));

        Map<Integer, List<Integer>> next = new LinkedHashMap<>();
        for (int f = 0; f < floors.size() - 1; f++) {
            connectFloors(floors.get(f), floors.get(f + 1), rng, next);
        }

        List<RunNode> nodes = new ArrayList<>();
        for (Map.Entry<Integer, RunNodeType> entry : types.entrySet()) {
            nodes.add(new RunNode(entry.getKey(), entry.getValue(), next.getOrDefault(entry.getKey(), List.of())));
        }
        return new RunMap(nodes, 0);
    }

    private static RunNodeType randomMiddleNodeType(Random rng) {
        int roll = rng.nextInt(100);
        if (roll < 60) return RunNodeType.BATTLE;
        if (roll < 80) return RunNodeType.ELITE;
        return RunNodeType.REST;
    }

    /**
     * Connects every node in {@code fromFloor} to 1-2 random nodes in {@code toFloor}, then
     * backfills any {@code toFloor} node that ended up with no incoming edge from a random
     * {@code fromFloor} node — guaranteeing every {@code toFloor} node is reachable without
     * ever leaving a {@code fromFloor} node with zero outgoing edges.
     */
    private static void connectFloors(List<Integer> fromFloor, List<Integer> toFloor, Random rng,
                                        Map<Integer, List<Integer>> next) {
        Set<Integer> covered = new HashSet<>();
        for (int from : fromFloor) {
            int edgeCount = 1 + rng.nextInt(2);
            Set<Integer> chosen = new LinkedHashSet<>();
            for (int k = 0; k < edgeCount; k++) {
                chosen.add(toFloor.get(rng.nextInt(toFloor.size())));
            }
            next.put(from, new ArrayList<>(chosen));
            covered.addAll(chosen);
        }
        for (int target : toFloor) {
            if (!covered.contains(target)) {
                int from = fromFloor.get(rng.nextInt(fromFloor.size()));
                next.get(from).add(target);
                covered.add(target);
            }
        }
    }
}
