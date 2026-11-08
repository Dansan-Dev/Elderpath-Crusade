package io.github.elderpath_crusade.game;

import io.github.elderpath_crusade.client.LocalMatchSpec;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.run.RunDifficulty;
import io.github.elderpath_crusade.run.RunState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RunManager's own decision logic (spec building, outcome resolution, guards) — no live
 * ClientMatchSession/child-process server involved, so this needs no assets and runs in every
 * profile. The real session wiring is covered separately by an @RequiresAssets test.
 */
class RunManagerTest {
    private static final List<String> STARTING_DECK = List.of("Wolf", "Wolf", "Wolf Cub", "Wolf Cub");

    @Test
    void startRun_createsARunStateAtThePlaceholderMapsStartNode() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK);

        RunState state = manager.getRunState();
        assertNotNull(state);
        assertTrue(manager.hasActiveRun());
        assertFalse(manager.isNodeInProgress());
        assertEquals(0, state.currentNode().id());
        assertEquals(RunManager.PLACEHOLDER_NODE_COUNT, state.map().nodes().size());
    }

    @Test
    void buildSpecForCurrentNode_mirrorsTheRunsDeckWithThePlayerAsP1() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK);

        LocalMatchSpec spec = manager.buildSpecForCurrentNode();
        assertEquals(STARTING_DECK, spec.decks().get(PieceAlignment.P1));
        assertEquals(STARTING_DECK, spec.decks().get(PieceAlignment.P2));
        assertTrue(spec.bots().containsKey(PieceAlignment.P2));
        assertFalse(spec.bots().containsKey(PieceAlignment.P1));
    }

    @Test
    void buildSpecForCurrentNode_requiresAnActiveRun() {
        RunManager manager = new RunManager();
        assertThrows(IllegalStateException.class, manager::buildSpecForCurrentNode);
    }

    @Test
    void reportNodeOutcome_loss_endsTheRunAsPermadeath() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK);

        manager.reportNodeOutcome(PieceAlignment.P2);

        RunState state = manager.getRunState();
        assertTrue(state.isEnded());
        assertFalse(state.isWon());
        assertFalse(manager.hasActiveRun());
    }

    @Test
    void reportNodeOutcome_nullWinner_isTreatedAsALoss() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK);

        manager.reportNodeOutcome(null);

        assertTrue(manager.getRunState().isEnded());
        assertFalse(manager.getRunState().isWon());
    }

    @Test
    void reportNodeOutcome_winOnANonTerminalNode_leavesTheRunGoing() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK);

        manager.reportNodeOutcome(PieceAlignment.P1);

        RunState state = manager.getRunState();
        assertFalse(state.isEnded());
        assertEquals(0, state.currentNode().id());
        assertFalse(state.currentNode().isTerminal());
    }

    @Test
    void reportNodeOutcome_winOnTheBossNode_endsTheRunAsWon() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK);
        RunState state = manager.getRunState();
        // walk to the map's terminal (Boss) node
        while (!state.currentNode().isTerminal()) {
            state.advanceTo(state.reachableNodes().get(0).id());
        }

        manager.reportNodeOutcome(PieceAlignment.P1);

        assertTrue(state.isEnded());
        assertTrue(state.isWon());
    }

    @Test
    void advanceToNextNode_delegatesToRunState() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK);
        manager.reportNodeOutcome(PieceAlignment.P1); // win the first node, stay put

        manager.advanceToNextNode(1);

        assertEquals(1, manager.getRunState().currentNode().id());
    }

    @Test
    void advanceToNextNode_requiresAnActiveRun() {
        RunManager manager = new RunManager();
        assertThrows(IllegalStateException.class, () -> manager.advanceToNextNode(1));
    }

    @Test
    void reportNodeOutcome_rejectsAfterTheRunHasAlreadyEnded() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK);
        manager.reportNodeOutcome(PieceAlignment.P2); // loss, run ends

        assertThrows(IllegalStateException.class, () -> manager.reportNodeOutcome(PieceAlignment.P1));
    }

    @Test
    void pollActiveNode_requiresANodeToHaveBeenStarted() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK);
        assertThrows(IllegalStateException.class, manager::pollActiveNode);
    }
}
