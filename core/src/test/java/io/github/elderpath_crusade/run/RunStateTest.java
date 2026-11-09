package io.github.elderpath_crusade.run;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RunStateTest {

    private static RunState freshRun(int battleCount) {
        return RunState.start(RunMap.linearChain(battleCount), RunDifficulty.DEFAULT,
                List.of("Wolf", "Wolf", "Wolf Cub", "Wolf Cub"));
    }

    @Test
    void start_buildsSequentialInstanceIdsInDeckOrder() {
        RunState run = freshRun(2);
        List<RunCardInstance> deck = run.deck();
        assertEquals(4, deck.size());
        assertEquals("run-0", deck.get(0).instanceId());
        assertEquals("Wolf", deck.get(0).cardName());
        assertEquals("run-3", deck.get(3).instanceId());
        assertEquals("Wolf Cub", deck.get(3).cardName());
        assertFalse(deck.get(0).isModified());
    }

    @Test
    void start_beginsAtTheMapsStartNode() {
        RunState run = freshRun(3);
        assertEquals(0, run.currentNode().id());
        assertEquals(Set.of(0), run.visitedNodeIds());
        assertFalse(run.isEnded());
    }

    @Test
    void currentNodeDepth_tracksTheCurrentNodeAsItAdvances() {
        RunState run = freshRun(3);
        assertEquals(0, run.currentNodeDepth());
        run.markCurrentNodeCleared();
        run.advanceTo(1);
        assertEquals(1, run.currentNodeDepth());
    }

    @Test
    void advanceTo_movesToAReachableNodeOnceCleared() {
        RunState run = freshRun(3);
        run.markCurrentNodeCleared();
        run.advanceTo(1);
        assertEquals(1, run.currentNode().id());
        assertEquals(Set.of(0, 1), run.visitedNodeIds());
        assertFalse(run.isCurrentNodeCleared());
    }

    @Test
    void advanceTo_rejectsBeforeTheCurrentNodeIsCleared() {
        RunState run = freshRun(3);
        assertFalse(run.isCurrentNodeCleared());
        assertThrows(IllegalStateException.class, () -> run.advanceTo(1));
    }

    @Test
    void advanceTo_rejectsANodeThatIsNotReachable() {
        RunState run = freshRun(3);
        run.markCurrentNodeCleared();
        assertThrows(IllegalArgumentException.class, () -> run.advanceTo(2));
    }

    @Test
    void advanceTo_rejectsAfterTheRunHasEnded() {
        RunState run = freshRun(3);
        run.endRun(false);
        assertThrows(IllegalStateException.class, () -> run.advanceTo(1));
    }

    @Test
    void markCurrentNodeCleared_rejectsAfterTheRunHasEnded() {
        RunState run = freshRun(2);
        run.endRun(false);
        assertThrows(IllegalStateException.class, run::markCurrentNodeCleared);
    }

    @Test
    void endRun_loss_setsEndedAndNotWon() {
        RunState run = freshRun(2);
        run.endRun(false);
        assertTrue(run.isEnded());
        assertFalse(run.isWon());
    }

    @Test
    void endRun_win_setsEndedAndWon() {
        RunState run = freshRun(2);
        run.endRun(true);
        assertTrue(run.isEnded());
        assertTrue(run.isWon());
    }

    @Test
    void endRun_rejectsEndingTwice() {
        RunState run = freshRun(2);
        run.endRun(false);
        assertThrows(IllegalStateException.class, () -> run.endRun(true));
    }

    @Test
    void addCardToDeck_appendsANewInstance() {
        RunState run = freshRun(2);
        run.addCardToDeck(RunCardInstance.unmodified("reward-1", "Shockling"));
        assertEquals(5, run.deck().size());
        assertEquals("Shockling", run.deck().get(4).cardName());
    }

    @Test
    void addCardToDeck_rejectsDuplicateInstanceId() {
        RunState run = freshRun(2);
        assertThrows(IllegalArgumentException.class,
                () -> run.addCardToDeck(RunCardInstance.unmodified("run-0", "Shockling")));
    }

    @Test
    void modifyCard_replacesTheInstanceInPlaceByInstanceId() {
        RunState run = freshRun(2);
        RunCardModification mod = new RunCardModification(Map.of("damage", 2), List.of());
        run.modifyCard("run-0", mod);

        RunCardInstance modified = run.deck().get(0);
        assertEquals("run-0", modified.instanceId());
        assertEquals("Wolf", modified.cardName());
        assertTrue(modified.isModified());
        assertEquals(mod, modified.modification());
        // every other instance is untouched
        assertFalse(run.deck().get(1).isModified());
    }

    @Test
    void modifyCard_rejectsUnknownInstanceId() {
        RunState run = freshRun(2);
        assertThrows(IllegalArgumentException.class, () -> run.modifyCard("nope", RunCardModification.NONE));
    }

    @Test
    void addElder_movesTheInstanceFromDeckToElders() {
        RunState run = freshRun(2); // 4-card deck: run-0..run-3

        run.addElder("run-1");

        assertEquals(3, run.deck().size());
        assertTrue(run.deck().stream().noneMatch(c -> c.instanceId().equals("run-1")));
        assertEquals(1, run.elders().size());
        assertEquals("run-1", run.elders().get(0).instanceId());
    }

    @Test
    void startingDeckSize_isFixedAndUnaffectedByPromotingElders() {
        RunState run = freshRun(2); // 4-card deck
        assertEquals(4, run.startingDeckSize());

        run.addElder("run-1");
        run.addElder("run-2");

        assertEquals(4, run.startingDeckSize(), "a run's starting deck size never changes");
        assertEquals(2, run.deck().size(), "only the current, drawable deck shrinks");
    }

    @Test
    void addElder_eachPromotionAccumulates() {
        RunState run = freshRun(2);

        run.addElder("run-0");
        run.addElder("run-1");

        assertEquals(2, run.deck().size());
        assertEquals(List.of("run-0", "run-1"),
                run.elders().stream().map(RunCardInstance::instanceId).toList());
    }

    @Test
    void addElder_rejectsAnUnknownInstanceId() {
        RunState run = freshRun(2);
        assertThrows(IllegalArgumentException.class, () -> run.addElder("no-such-id"));
    }

    @Test
    void addElder_rejectsShrinkingTheDeckToEmpty() {
        RunState run = new RunState(RunMap.linearChain(1), RunDifficulty.DEFAULT,
                List.of(RunCardInstance.unmodified("run-0", "Wolf")));
        assertThrows(IllegalStateException.class, () -> run.addElder("run-0"));
    }

    @Test
    void addElder_rejectsAfterTheRunHasEnded() {
        RunState run = freshRun(2);
        run.endRun(false);
        assertThrows(IllegalStateException.class, () -> run.addElder("run-0"));
    }

    @Test
    void addRelic_tracksActiveRelicIds() {
        RunState run = freshRun(2);
        run.addRelic("PackLeader");
        assertEquals(List.of("PackLeader"), run.activeRelicIds());
    }

    @Test
    void start_rejectsAnEmptyStartingDeck() {
        assertThrows(IllegalArgumentException.class,
                () -> RunState.start(RunMap.linearChain(1), RunDifficulty.DEFAULT, List.of()));
    }

    @Test
    void reachableNodes_isEmptyAtATerminalNode() {
        RunState run = freshRun(2);
        run.markCurrentNodeCleared();
        run.advanceTo(1);
        assertTrue(run.reachableNodes().isEmpty());
        assertTrue(run.currentNode().isTerminal());
    }
}
