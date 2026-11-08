package io.github.elderpath_crusade.run;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RunMapTest {

    @Test
    void linearChain_endsInABossNode() {
        RunMap map = RunMap.linearChain(3);
        assertEquals(RunNodeType.BATTLE, map.node(0).type());
        assertEquals(RunNodeType.BATTLE, map.node(1).type());
        assertEquals(RunNodeType.BOSS, map.node(2).type());
        assertTrue(map.node(2).isTerminal());
        assertEquals(0, map.startNodeId());
    }

    @Test
    void linearChain_everyNonLastNodeReachesExactlyTheNext() {
        RunMap map = RunMap.linearChain(4);
        assertEquals(List.of(map.node(1)), map.reachableFrom(0));
        assertEquals(List.of(map.node(2)), map.reachableFrom(1));
        assertEquals(List.of(map.node(3)), map.reachableFrom(2));
        assertTrue(map.reachableFrom(3).isEmpty());
    }

    @Test
    void linearChain_ofOne_isJustTheBoss() {
        RunMap map = RunMap.linearChain(1);
        assertEquals(1, map.nodes().size());
        assertEquals(RunNodeType.BOSS, map.node(0).type());
    }

    @Test
    void linearChain_rejectsZeroOrNegative() {
        assertThrows(IllegalArgumentException.class, () -> RunMap.linearChain(0));
        assertThrows(IllegalArgumentException.class, () -> RunMap.linearChain(-1));
    }

    @Test
    void constructor_rejectsDuplicateNodeIds() {
        List<RunNode> nodes = List.of(
                new RunNode(0, RunNodeType.BATTLE, List.of()),
                new RunNode(0, RunNodeType.BOSS, List.of())
        );
        assertThrows(IllegalArgumentException.class, () -> new RunMap(nodes, 0));
    }

    @Test
    void constructor_rejectsStartNodeNotInMap() {
        List<RunNode> nodes = List.of(new RunNode(0, RunNodeType.BOSS, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new RunMap(nodes, 7));
    }

    @Test
    void constructor_rejectsNextPointingAtUnknownNode() {
        List<RunNode> nodes = List.of(new RunNode(0, RunNodeType.BATTLE, List.of(99)));
        assertThrows(IllegalArgumentException.class, () -> new RunMap(nodes, 0));
    }

    @Test
    void constructor_rejectsEmptyNodeList() {
        assertThrows(IllegalArgumentException.class, () -> new RunMap(List.of(), 0));
    }

    @Test
    void node_throwsForUnknownId() {
        RunMap map = RunMap.linearChain(2);
        assertThrows(IllegalArgumentException.class, () -> map.node(99));
    }

    // --- generateBranching (MAP-001) ---

    @Test
    void generateBranching_startsOnABattleNode() {
        for (long seed = 1; seed <= 20; seed++) {
            RunMap map = RunMap.generateBranching(seed, 4, 3);
            assertEquals(RunNodeType.BATTLE, map.node(map.startNodeId()).type());
        }
    }

    @Test
    void generateBranching_hasExactlyOneTerminalNodeAndItsTheBoss() {
        for (long seed = 1; seed <= 20; seed++) {
            RunMap map = RunMap.generateBranching(seed, 4, 3);
            List<RunNode> terminal = map.nodes().stream().filter(RunNode::isTerminal).toList();
            assertEquals(1, terminal.size(), "seed " + seed);
            assertEquals(RunNodeType.BOSS, terminal.get(0).type(), "seed " + seed);
        }
    }

    @Test
    void generateBranching_noDeadEndsBeforeTheBoss() {
        for (long seed = 1; seed <= 20; seed++) {
            RunMap map = RunMap.generateBranching(seed, 4, 3);
            for (RunNode node : map.nodes()) {
                if (node.type() != RunNodeType.BOSS) {
                    assertFalse(node.next().isEmpty(), "seed " + seed + " node " + node.id() + " is a dead end");
                }
            }
        }
    }

    @Test
    void generateBranching_everyNodeIsReachableFromStart() {
        for (long seed = 1; seed <= 20; seed++) {
            RunMap map = RunMap.generateBranching(seed, 4, 3);
            Set<Integer> reached = reachableFromStart(map);
            Set<Integer> allIds = map.nodes().stream().map(RunNode::id).collect(java.util.stream.Collectors.toSet());
            assertEquals(allIds, reached, "seed " + seed);
        }
    }

    @Test
    void generateBranching_sameSeedProducesTheSameMap() {
        RunMap first = RunMap.generateBranching(42, 4, 3);
        RunMap second = RunMap.generateBranching(42, 4, 3);
        assertEquals(first, second);
    }

    @Test
    void generateBranching_differentSeedsCanProduceDifferentMaps() {
        RunMap a = RunMap.generateBranching(1, 4, 3);
        RunMap b = RunMap.generateBranching(2, 4, 3);
        assertNotEquals(a, b);
    }

    @Test
    void generateBranching_distributionIncludesEliteAndRestAcrossSeeds() {
        Set<RunNodeType> seen = new HashSet<>();
        for (long seed = 1; seed <= 50; seed++) {
            RunMap map = RunMap.generateBranching(seed, 4, 3);
            for (RunNode node : map.nodes()) seen.add(node.type());
        }
        assertTrue(seen.contains(RunNodeType.ELITE), "never saw an Elite node across 50 seeds");
        assertTrue(seen.contains(RunNodeType.REST), "never saw a Rest node across 50 seeds");
    }

    @Test
    void generateBranching_rejectsInvalidShape() {
        assertThrows(IllegalArgumentException.class, () -> RunMap.generateBranching(1, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> RunMap.generateBranching(1, 4, 0));
    }

    @Test
    void generate_defaultShapeHasTheExpectedNodeCount() {
        // 1 start + 4 middle floors * 3 width + 1 boss
        RunMap map = RunMap.generate(123);
        assertEquals(14, map.nodes().size());
    }

    private static Set<Integer> reachableFromStart(RunMap map) {
        Set<Integer> visited = new HashSet<>();
        Deque<Integer> queue = new ArrayDeque<>();
        visited.add(map.startNodeId());
        queue.add(map.startNodeId());
        while (!queue.isEmpty()) {
            int id = queue.poll();
            for (RunNode next : map.reachableFrom(id)) {
                if (visited.add(next.id())) queue.add(next.id());
            }
        }
        return visited;
    }
}
