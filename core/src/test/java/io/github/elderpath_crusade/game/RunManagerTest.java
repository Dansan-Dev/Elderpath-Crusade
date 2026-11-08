package io.github.elderpath_crusade.game;

import io.github.elderpath_crusade.client.LocalMatchSpec;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.run.RunDifficulty;
import io.github.elderpath_crusade.run.RunNodeType;
import io.github.elderpath_crusade.run.RunState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RunManager's own decision logic (spec building, outcome resolution, guards) — no live
 * ClientMatchSession/child-process server involved, so this needs no assets and runs in every
 * profile. The real session wiring is covered separately by an @RequiresAssets test. Uses the
 * seeded startRun overload throughout for determinism — the real map generator (MAP-001) can
 * branch, so tests reason off the map's actual shape rather than assuming a fixed id sequence.
 */
class RunManagerTest {
    private static final List<String> STARTING_DECK = List.of("Wolf", "Wolf", "Wolf Cub", "Wolf Cub");
    private static final long SEED = 42L;

    @Test
    void startRun_createsARunStateAtTheMapsStartNode() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);

        RunState state = manager.getRunState();
        assertNotNull(state);
        assertTrue(manager.hasActiveRun());
        assertFalse(manager.isNodeInProgress());
        assertEquals(state.map().startNodeId(), state.currentNode().id());
        assertEquals(RunNodeType.BATTLE, state.currentNode().type());
        assertFalse(state.isCurrentNodeCleared());
        assertTrue(state.map().nodes().size() > 1);
    }

    @Test
    void buildSpecForCurrentNode_mirrorsTheRunsDeckWithThePlayerAsP1() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);

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
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);

        manager.reportNodeOutcome(PieceAlignment.P2);

        RunState state = manager.getRunState();
        assertTrue(state.isEnded());
        assertFalse(state.isWon());
        assertFalse(manager.hasActiveRun());
    }

    @Test
    void reportNodeOutcome_nullWinner_isTreatedAsALoss() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);

        manager.reportNodeOutcome(null);

        assertTrue(manager.getRunState().isEnded());
        assertFalse(manager.getRunState().isWon());
    }

    @Test
    void reportNodeOutcome_winOnANonTerminalNode_marksItClearedWithoutAdvancing() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);
        RunState state = manager.getRunState();
        int startId = state.currentNode().id();

        manager.reportNodeOutcome(PieceAlignment.P1);

        assertFalse(state.isEnded());
        assertEquals(startId, state.currentNode().id(), "a win doesn't move the player — picking the next node does");
        assertTrue(state.isCurrentNodeCleared());
    }

    @Test
    void reportNodeOutcome_winOnTheBossNode_endsTheRunAsWon() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);
        RunState state = manager.getRunState();
        // win/advance through every node up to (not including) the Boss
        while (!state.currentNode().isTerminal()) {
            if (!state.isCurrentNodeCleared()) manager.reportNodeOutcome(PieceAlignment.P1);
            manager.advanceToNextNode(state.reachableNodes().get(0).id());
        }

        manager.reportNodeOutcome(PieceAlignment.P1);

        assertTrue(state.isEnded());
        assertTrue(state.isWon());
    }

    @Test
    void advanceToNextNode_delegatesToRunStateOnceTheCurrentNodeIsCleared() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);
        manager.reportNodeOutcome(PieceAlignment.P1); // win the start node, now cleared

        int next = manager.getRunState().reachableNodes().get(0).id();
        manager.advanceToNextNode(next);

        assertEquals(next, manager.getRunState().currentNode().id());
    }

    @Test
    void advanceToNextNode_requiresAnActiveRun() {
        RunManager manager = new RunManager();
        assertThrows(IllegalStateException.class, () -> manager.advanceToNextNode(1));
    }

    @Test
    void reportNodeOutcome_rejectsAfterTheRunHasAlreadyEnded() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);
        manager.reportNodeOutcome(PieceAlignment.P2); // loss, run ends

        assertThrows(IllegalStateException.class, () -> manager.reportNodeOutcome(PieceAlignment.P1));
    }

    @Test
    void pollActiveNode_requiresANodeToHaveBeenStarted() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);
        assertThrows(IllegalStateException.class, manager::pollActiveNode);
    }
}
