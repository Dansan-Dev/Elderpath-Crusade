package io.github.elderpath_crusade.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.elderpath_crusade.data.AbilityRef;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game.RunManager;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** RUN-004: a run survives closing the game — save format round trip, bad files, and RunManager's save points. */
class RunPersistenceTest {
    @TempDir File dir;
    File file;

    @BeforeEach
    void setUp() {
        file = new File(dir, "sub/run.json");
    }

    private RunManager newManager() {
        RunManager manager = new RunManager();
        manager.setSaveStore(new JsonRunSaveStore(file));
        return manager;
    }

    private static RunCardModification modification() {
        return new RunCardModification(Map.of("damage", 2, "cost", -1),
                List.of(new AbilityRef("Frenzy"), new AbilityRef("RangeBonus", Map.of("addRange", 3))));
    }

    @Test
    void roundTrip_keepsEverythingTheRunNeeds() {
        RunManager manager = newManager();
        manager.startRun(new RunDifficulty(3), List.of("Wolf", "Wolf Cub", "Crow", "Rogue"), 42L);
        manager.upgradeCard("run-1", modification());
        manager.promoteToElder("run-0");
        manager.addRelic("some-relic");
        manager.reportNodeOutcome(PieceAlignment.P1); // start node cleared
        int next = manager.getRunState().reachableNodes().get(0).id();
        manager.advanceToNextNode(next);

        RunState before = manager.getRunState();
        RunManager reopened = newManager();
        RunState after = reopened.getRunState();

        assertTrue(reopened.hasActiveRun());
        assertEquals(before.difficulty(), after.difficulty());
        assertEquals(before.deck(), after.deck());
        assertEquals(before.elders(), after.elders());
        assertEquals(before.activeRelicIds(), after.activeRelicIds());
        assertEquals(before.currentNode(), after.currentNode());
        assertEquals(before.isCurrentNodeCleared(), after.isCurrentNodeCleared());
        assertEquals(before.visitedNodeIds(), after.visitedNodeIds());
        assertEquals(before.map(), after.map());
        assertEquals(before.floorIndex(), after.floorIndex());
        assertEquals(before.startingDeckSize(), after.startingDeckSize());
        assertEquals(before.startedAtMillis(), after.startedAtMillis());
        assertEquals(modification(), after.deck().stream().filter(c -> c.instanceId().equals("run-1")).findFirst().get().modification());
    }

    @Test
    void aLostRun_removesItsSave() {
        RunManager manager = newManager();
        manager.startRun(RunDifficulty.DEFAULT, List.of("Wolf", "Wolf Cub"), 1L);
        assertTrue(file.isFile());
        manager.reportNodeOutcome(PieceAlignment.P2);
        assertFalse(file.exists());
        assertFalse(newManager().hasActiveRun());
    }

    @Test
    void abandonRun_removesTheSaveAndTheRun() {
        RunManager manager = newManager();
        manager.startRun(RunDifficulty.DEFAULT, List.of("Wolf", "Wolf Cub"), 1L);
        manager.abandonRun();
        assertFalse(manager.hasActiveRun());
        assertFalse(file.exists());
        assertFalse(newManager().hasActiveRun());
    }

    @Test
    void withoutAStore_nothingIsWritten() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, List.of("Wolf", "Wolf Cub"), 1L);
        assertTrue(manager.hasActiveRun());
        assertFalse(file.exists());
    }

    @Test
    void anUnreadableSave_isSetAsideNotDeleted() throws Exception {
        file.getParentFile().mkdirs();
        Files.writeString(file.toPath(), "{ not json");
        assertFalse(newManager().hasActiveRun());
        assertFalse(file.exists());
        assertTrue(new File(file.getPath() + ".unreadable").isFile());
    }

    @Test
    void aSaveFromAnotherFormatVersion_isRefusedAndKept() {
        RunManager manager = newManager();
        manager.startRun(RunDifficulty.DEFAULT, List.of("Wolf", "Wolf Cub"), 1L);
        RunSnapshot current = new JsonRunSaveStore(file).load().orElseThrow();
        RunSnapshot future = new RunSnapshot(RunSnapshot.FORMAT_VERSION + 1, current.gameVersion(), current.difficulty(),
                current.floorIndex(), current.totalFloors(), current.startingDeckSize(), current.deck(), current.elders(),
                current.relics(), current.visitedNodeIds(), current.currentNodeId(), current.currentNodeCleared(),
                current.startedAtMillis(), current.map());
        new JsonRunSaveStore(file).save(future);

        assertFalse(newManager().hasActiveRun());
        assertTrue(new File(file.getPath() + ".unreadable").isFile());
    }

    @Test
    void aSaveWhosePositionIsNotInItsMap_isIgnored() {
        RunManager manager = newManager();
        manager.startRun(RunDifficulty.DEFAULT, List.of("Wolf", "Wolf Cub"), 1L);
        RunSnapshot s = new JsonRunSaveStore(file).load().orElseThrow();
        new JsonRunSaveStore(file).save(new RunSnapshot(s.formatVersion(), s.gameVersion(), s.difficulty(), s.floorIndex(),
                s.totalFloors(), s.startingDeckSize(), s.deck(), s.elders(), s.relics(), s.visitedNodeIds(), 9999,
                s.currentNodeCleared(), s.startedAtMillis(), s.map()));
        assertFalse(newManager().hasActiveRun());
    }

    @Test
    void startingANewRun_replacesTheSavedOne() {
        RunManager manager = newManager();
        manager.startRun(RunDifficulty.DEFAULT, List.of("Wolf", "Wolf Cub"), 1L);
        manager.startRun(new RunDifficulty(5), List.of("Crow", "Rogue", "Wolf"), 2L);
        RunState loaded = newManager().getRunState();
        assertEquals(5, loaded.difficulty().level());
        assertEquals(3, loaded.deck().size());
    }
}
