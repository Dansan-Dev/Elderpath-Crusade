package io.github.elderpath_crusade.game;

import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.client.LocalMatchSpec;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.run.RunCardInstance;
import io.github.elderpath_crusade.run.RunDifficulty;
import io.github.elderpath_crusade.run.RunMap;
import io.github.elderpath_crusade.run.RunState;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side orchestrator for a roguelike run (FEAT-006) — owns the active {@link RunState},
 * builds and launches each node's battle via {@link ClientMatchSession#startLocal}, and resolves
 * that battle's outcome against RunState once it's known (advance on a win, permadeath on a
 * loss). {@code GameContext}-held, like {@code DeckManager}/{@code GameModeManager}.
 *
 * <p>No relic/card-modification/difficulty plumbing yet (LAUNCH-001, Phase 3 of
 * roguelike-run-plan.md) — this ticket (RUN-002) proves the spine with a plain deck and a fixed
 * bot opponent. The map is {@link RunMap#linearChain}'s placeholder until MAP-001's real
 * generator exists.
 */
public final class RunManager {
    /** The placeholder map's length until MAP-001's real generator exists. */
    public static final int PLACEHOLDER_NODE_COUNT = 5;
    private static final BotKind NODE_BOT_KIND = BotKind.SMART;

    private RunState runState;
    private ClientMatchSession activeSession;

    public RunManager() {
    }

    /** Starts a brand-new run: a fresh placeholder map and deck, nothing played yet. */
    public void startRun(RunDifficulty difficulty, List<String> startingDeckCardNames) {
        RunMap map = RunMap.linearChain(PLACEHOLDER_NODE_COUNT);
        this.runState = RunState.start(map, difficulty, startingDeckCardNames);
        this.activeSession = null;
    }

    public RunState getRunState() {
        return runState;
    }

    public boolean hasActiveRun() {
        return runState != null && !runState.isEnded();
    }

    /** Whether a node's battle is currently in progress through this class's own session. */
    public boolean isNodeInProgress() {
        return activeSession != null;
    }

    /**
     * Builds the {@link LocalMatchSpec} for the run's current node. The player's deck is the
     * run's current deck's card names (ignoring any per-instance modification for now — see
     * UPG-003); the opponent is a fixed bot deck/kind (DIFF-001 replaces this with the
     * difficulty model's resolved knobs). The player always plays P1.
     */
    public LocalMatchSpec buildSpecForCurrentNode() {
        requireActiveRun();
        List<String> playerDeck = deckCardNames();
        List<String> botDeck = List.copyOf(playerDeck);
        return LocalMatchSpec.solo(playerDeck, botDeck, NODE_BOT_KIND);
    }

    private List<String> deckCardNames() {
        List<String> names = new ArrayList<>();
        for (RunCardInstance instance : runState.deck()) names.add(instance.cardName());
        return names;
    }

    /**
     * Launches the current node's battle and keeps the session for {@link #pollActiveNode()}.
     * A room that drives its own {@code ClientMatchSession}/{@code BattleRoom} polling instead
     * can skip this and call {@link #reportNodeOutcome} directly once it knows the winner.
     */
    public ClientMatchSession startCurrentNode() {
        requireActiveRun();
        if (activeSession != null) throw new IllegalStateException("a node is already in progress");
        activeSession = ClientMatchSession.startLocal(buildSpecForCurrentNode());
        return activeSession;
    }

    public ClientMatchSession activeSession() {
        return activeSession;
    }

    /**
     * Polls the node started by {@link #startCurrentNode()} and, once its match has ended,
     * resolves the outcome against RunState and clears the session. Returns true once resolved
     * (false while still in progress) — call every frame, or in a tight loop for a headless
     * scripted run, until it returns true.
     */
    public boolean pollActiveNode() {
        if (activeSession == null) throw new IllegalStateException("no node in progress");
        activeSession.poll();
        if (!activeSession.getStatus().isOver()) return false;
        PieceAlignment winner = activeSession.getWinner();
        activeSession.close();
        activeSession = null;
        reportNodeOutcome(winner);
        return true;
    }

    /**
     * Resolves a node's battle outcome against RunState — ends the run as a loss (permadeath)
     * on anything but a P1 win (the player always plays P1, so a null/non-P1 winner, e.g. a
     * draw, counts as a loss); on a win, ends the run as won if the current node was the map's
     * terminal/Boss node, otherwise advances straight to the next node. Public so a room
     * managing its own session can report the result without going through
     * {@link #startCurrentNode}/{@link #pollActiveNode}.
     *
     * <p>The placeholder linear map ({@link RunMap#linearChain}) never has more than one
     * reachable node, so there's nothing for the player to pick — auto-advancing is both
     * simpler and correct for it. A real branching map (MAP-001/002) will have genuine choices;
     * {@link #advanceToNextNode} stays public for the room that lets the player make one.
     */
    public void reportNodeOutcome(PieceAlignment winner) {
        requireActiveRun();
        boolean won = winner == PieceAlignment.P1;
        if (!won) {
            runState.endRun(false);
            return;
        }
        if (runState.currentNode().isTerminal()) {
            runState.endRun(true);
            return;
        }
        runState.advanceTo(runState.reachableNodes().get(0).id());
    }

    /** Moves to the next node the player picked (only valid once the current node's battle is won). */
    public void advanceToNextNode(int nodeId) {
        requireActiveRun();
        runState.advanceTo(nodeId);
    }

    private void requireActiveRun() {
        if (runState == null) throw new IllegalStateException("no active run — call startRun first");
        if (runState.isEnded()) throw new IllegalStateException("the run has already ended");
    }
}
