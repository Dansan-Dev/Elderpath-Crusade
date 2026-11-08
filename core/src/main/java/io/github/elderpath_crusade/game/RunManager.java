package io.github.elderpath_crusade.game;

import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.client.LocalMatchSpec;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.run.RunCardInstance;
import io.github.elderpath_crusade.run.RunDifficulty;
import io.github.elderpath_crusade.run.RunMap;
import io.github.elderpath_crusade.run.RunState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Client-side orchestrator for a roguelike run (FEAT-006) — owns the active {@link RunState},
 * builds and launches each node's battle via {@link ClientMatchSession#startLocal}, and resolves
 * that battle's outcome against RunState once it's known (advance on a win, permadeath on a
 * loss). {@code GameContext}-held, like {@code DeckManager}/{@code GameModeManager}.
 *
 * <p>No relic/card-modification plumbing yet (LAUNCH-001, Phase 3 of roguelike-run-plan.md) —
 * the bot's deck is still the player's own deck, just padded out to
 * {@link RunDifficulty#resolveForNode}'s resolved size. The map is {@link RunMap#generate}'s
 * real seeded branching generator (MAP-001).
 */
public final class RunManager {
    private RunState runState;
    private ClientMatchSession activeSession;

    public RunManager() {
    }

    /** Starts a brand-new run with a randomly seeded map: a fresh deck, nothing played yet. */
    public void startRun(RunDifficulty difficulty, List<String> startingDeckCardNames) {
        startRun(difficulty, startingDeckCardNames, new Random().nextLong());
    }

    /** Starts a brand-new run with a specific map seed (deterministic — tests, or a future "share this run" feature). */
    public void startRun(RunDifficulty difficulty, List<String> startingDeckCardNames, long mapSeed) {
        RunMap map = RunMap.generate(mapSeed);
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
     * UPG-003); the opponent's bot kind and deck size come from
     * {@link RunDifficulty#resolveForNode} (the run's chosen level + this node's depth) — its
     * deck is the player's own cards, cycled to pad out to the resolved size. The player always
     * plays P1.
     */
    public LocalMatchSpec buildSpecForCurrentNode() {
        requireActiveRun();
        List<String> playerDeck = deckCardNames();
        RunDifficulty.ResolvedKnobs knobs = runState.difficulty().resolveForNode(
                runState.currentNodeDepth(), runState.currentNode().type(), playerDeck.size());
        List<String> botDeck = padDeck(playerDeck, knobs.botDeckSize());
        return LocalMatchSpec.solo(playerDeck, botDeck, knobs.botKind());
    }

    private List<String> deckCardNames() {
        List<String> names = new ArrayList<>();
        for (RunCardInstance instance : runState.deck()) names.add(instance.cardName());
        return names;
    }

    /** Cycles through {@code deck} to build a deck of exactly {@code size} cards. */
    private static List<String> padDeck(List<String> deck, int size) {
        List<String> padded = new ArrayList<>();
        for (int i = 0; i < size; i++) padded.add(deck.get(i % deck.size()));
        return padded;
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
     * terminal/Boss node, otherwise marks the current node cleared so the player can pick their
     * next one via {@link #advanceToNextNode}. Public so a room managing its own session can
     * report the result without going through {@link #startCurrentNode}/{@link #pollActiveNode}.
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
        runState.markCurrentNodeCleared();
    }

    /**
     * Moves to the node the player picked among {@link RunState#reachableNodes()} (only valid
     * once the current node is cleared). A non-battle node (e.g. Rest) has nothing to fight, so
     * it's immediately marked cleared on arrival — the room sees it as already resolved and the
     * player can pick straight past it.
     */
    public void advanceToNextNode(int nodeId) {
        requireActiveRun();
        runState.advanceTo(nodeId);
        if (!runState.currentNode().type().isBattle()) {
            runState.markCurrentNodeCleared();
        }
    }

    private void requireActiveRun() {
        if (runState == null) throw new IllegalStateException("no active run — call startRun first");
        if (runState.isEnded()) throw new IllegalStateException("the run has already ended");
    }
}
