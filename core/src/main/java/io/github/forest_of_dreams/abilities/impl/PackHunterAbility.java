package io.github.forest_of_dreams.abilities.impl;

import io.github.forest_of_dreams.abilities.PassiveAbility;
import io.github.forest_of_dreams.abilities.stats.StatsModifier;
import io.github.forest_of_dreams.abilities.TriggeredAbility;
import io.github.forest_of_dreams.characters.pieces.WolfCub;
import io.github.forest_of_dreams.enums.GamePieceData;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.game_objects.board.Board;
import io.github.forest_of_dreams.game_objects.board.GamePiece;
import io.github.forest_of_dreams.game_objects.board.MonsterGamePiece;
import io.github.forest_of_dreams.multiplayer.EventBus;
import io.github.forest_of_dreams.multiplayer.GameEvent;
import io.github.forest_of_dreams.multiplayer.GameEventType;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Pack Hunter (aura): A Wolf grants +1 attack to adjacent allied WolfCubs.
 * Implemented as a PassiveAbility that actively manages its StatsModifier in recipients' accumulators.
 */
public class PackHunterAbility implements PassiveAbility, TriggeredAbility {
    @Override
    public io.github.forest_of_dreams.abilities.AbilityType getType() { return io.github.forest_of_dreams.abilities.AbilityType.PASSIVE; }
    private final StatsModifier mod;
    private MonsterGamePiece owner;
    private final Set<MonsterGamePiece> appliedTo = new HashSet<>();
    // Event listeners for global re-evaluation
    private Consumer<GameEvent> moveListener;
    private Consumer<GameEvent> spawnListener;
    private Consumer<GameEvent> diedListener;

    public PackHunterAbility() {
        this.mod = new StatsModifier();
        this.mod.source = this;
        this.mod.addDamage = 1;
    }

    @Override
    public String getName() { return "Pack Hunter"; }

    @Override
    public String getDescription() { return PackHunterAbility.getAbilityDescription(); }

    public static String getAbilityDescription() {
        return "+1 attack to adjacent\nallied WolfCubs";
    }

    @Override
    public StatsModifier getModifier() { return mod; }

    @Override
    public boolean isConditionMet(MonsterGamePiece owner, Board board) {
        // Not used by the accumulator-based model; return false to avoid owner-local application.
        return false;
    }

    @Override
    public void onAttach(MonsterGamePiece owner) {
        this.owner = owner;
        registerGlobalListeners();
        refreshRecipients();
    }

    @Override
    public void onDetach() {
        unregisterGlobalListeners();
        // Remove the modifier from all recipients
        mod.clear();
        appliedTo.clear();
        this.owner = null;
    }

    @Override
    public void onOwnerSpawned(MonsterGamePiece owner, int row, int col) { refreshRecipients(); }

    @Override
    public void onOwnerMoved(MonsterGamePiece owner, int fromRow, int fromCol, int toRow, int toCol) { refreshRecipients(); }

    @Override
    public void onOwnerDied(MonsterGamePiece owner) { onDetach(); }

    @Override
    public void onGameEvent(GameEvent event) {
        if (owner == null) return;
        GameEventType t = event.getType();
        // Re-evaluate when any piece moves/spawns/dies on same board to keep adjacency correct
        if (t == GameEventType.PIECE_MOVED || t == GameEventType.PIECE_SPAWNED || t == GameEventType.PIECE_DIED) {
            refreshRecipients();
        }
    }

    private void registerGlobalListeners() {
        moveListener = this::onGameEvent;
        spawnListener = this::onGameEvent;
        diedListener = this::onGameEvent;
        EventBus.register(GameEventType.PIECE_MOVED, moveListener);
        EventBus.register(GameEventType.PIECE_SPAWNED, spawnListener);
        EventBus.register(GameEventType.PIECE_DIED, diedListener);
    }

    private void unregisterGlobalListeners() {
        if (moveListener != null) EventBus.unregister(GameEventType.PIECE_MOVED, moveListener);
        if (spawnListener != null) EventBus.unregister(GameEventType.PIECE_SPAWNED, spawnListener);
        if (diedListener != null) EventBus.unregister(GameEventType.PIECE_DIED, diedListener);
        moveListener = spawnListener = diedListener = null;
    }

    private void refreshRecipients() {
        if (owner == null) return;
        Object posObj = owner.getData(GamePieceData.POSITION);
        if (!(posObj instanceof Board.Position pos)) return;
        Board board = pos.getBoard();
        if (board == null) return;
        int r = pos.getRow();
        int c = pos.getCol();
        PieceAlignment align = owner.getAlignment();
        int[][] dirs = new int[][]{{1,0},{-1,0},{0,1},{0,-1}};
        Set<MonsterGamePiece> now = new HashSet<>();
        for (int[] d : dirs) {
            int nr = r + d[0];
            int nc = c + d[1];
            if (nr < 0 || nr >= board.getROWS() || nc < 0 || nc >= board.getCOLS()) continue;
            GamePiece gp = board.getGamePieceAtPos(nr, nc);
            if (gp instanceof MonsterGamePiece mgp) {
                if (mgp.getAlignment() == align && mgp instanceof WolfCub) {
                    now.add(mgp);
                }
            }
        }
        // Remove from pieces no longer eligible
        for (MonsterGamePiece prev : new HashSet<>(appliedTo)) {
            if (!now.contains(prev)) {
                prev.getStatsAccumulator().remove(mod);
                appliedTo.remove(prev);
            }
        }
        // Add to new recipients
        for (MonsterGamePiece target : now) {
            if (!appliedTo.contains(target)) {
                target.getStatsAccumulator().add(mod);
                appliedTo.add(target);
            }
        }
    }
}
