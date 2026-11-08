package io.github.elderpath_crusade.run;

import org.junit.jupiter.api.Test;

import java.util.List;

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
}
