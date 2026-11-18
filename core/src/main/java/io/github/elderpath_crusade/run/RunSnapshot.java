package io.github.elderpath_crusade.run;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything needed to put a roguelike run back exactly where it was between battles (RUN-004):
 * the current floor's map, position, deck and Elders (with each card's modification), relics,
 * difficulty and clock. A plain record, serialized as JSON by {@link JsonRunSaveStore}; {@link
 * #FORMAT_VERSION} is bumped whenever the shape changes so an older save is refused cleanly
 * instead of crashing. A battle in progress is not part of it — a run is saved when a node is
 * chosen and after every outcome, so resuming mid-battle restarts that node's battle.
 */
public record RunSnapshot(
        int formatVersion,
        String gameVersion,
        RunDifficulty difficulty,
        int floorIndex,
        int totalFloors,
        int startingDeckSize,
        List<RunCardInstance> deck,
        List<RunCardInstance> elders,
        List<String> relics,
        List<Integer> visitedNodeIds,
        int currentNodeId,
        boolean currentNodeCleared,
        long startedAtMillis,
        RunMap map) {

    public static final int FORMAT_VERSION = 1;

    public static RunSnapshot of(RunState state, String gameVersion) {
        List<Integer> visited = new ArrayList<>(state.visitedNodeIds());
        java.util.Collections.sort(visited);
        return new RunSnapshot(FORMAT_VERSION, gameVersion, state.difficulty(), state.floorIndex(), state.totalFloors(),
                state.startingDeckSize(), state.deck(), state.elders(), state.activeRelicIds(), visited,
                state.currentNode().id(), state.isCurrentNodeCleared(), state.startedAtMillis(), state.map());
    }

    /** Rebuilds the run; throws {@link IllegalArgumentException}/{@link IllegalStateException} for an inconsistent snapshot. */
    public RunState toState() {
        if (formatVersion != FORMAT_VERSION) throw new IllegalArgumentException("unsupported run save format " + formatVersion);
        if (map == null || difficulty == null || deck == null || elders == null || relics == null || visitedNodeIds == null) {
            throw new IllegalArgumentException("incomplete run save");
        }
        return RunState.restore(map, difficulty, deck, elders, relics, visitedNodeIds, currentNodeId, currentNodeCleared,
                floorIndex, totalFloors, startingDeckSize, startedAtMillis);
    }
}
