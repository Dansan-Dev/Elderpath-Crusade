package io.github.elderpath_crusade.abilities.impl._multi.aura;

import io.github.elderpath_crusade.abilities.AbilityType;
import io.github.elderpath_crusade.abilities.PassiveAbility;
import io.github.elderpath_crusade.abilities.stats.StatsModifier;
import io.github.elderpath_crusade.abilities.TriggeredAbility;
import io.github.elderpath_crusade.characters.pieces.WolfCub;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.GameEvent;
import io.github.elderpath_crusade.events.PieceDiedEvent;
import io.github.elderpath_crusade.events.PieceMovedEvent;
import io.github.elderpath_crusade.events.PieceSpawnedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Pack Hunter (aura): A Wolf grants +1 attack to adjacent allied WolfCubs.
 * Implemented as a PassiveAbility that actively manages its StatsModifier in recipients' accumulators.
 */
public class PackHunterAbility implements PassiveAbility, TriggeredAbility {
    @Override
    public AbilityType getType() { return AbilityType.PASSIVE; }
    private final StatsModifier mod;
    private MonsterGamePiece owner;
    private final Set<MonsterGamePiece> appliedTo = new HashSet<>();
    private Consumer<PieceMovedEvent> moveListener;
    private Consumer<PieceSpawnedEvent> spawnListener;
    private Consumer<PieceDiedEvent> diedListener;

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
        return "+1 attack to adjacent allied Wolf Cubs";
    }

    @Override
    public StatsModifier getModifier() { return mod; }

    @Override
    public boolean isConditionMet(MonsterGamePiece owner, Board board) {
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
        refreshRecipients();
    }

    private void registerGlobalListeners() {
        moveListener = e -> onGameEvent(e);
        spawnListener = e -> onGameEvent(e);
        diedListener = e -> onGameEvent(e);
        TypedEventBus.get().register(PieceMovedEvent.class, moveListener);
        TypedEventBus.get().register(PieceSpawnedEvent.class, spawnListener);
        TypedEventBus.get().register(PieceDiedEvent.class, diedListener);
    }

    private void unregisterGlobalListeners() {
        if (moveListener != null) TypedEventBus.get().unregister(PieceMovedEvent.class, moveListener);
        if (spawnListener != null) TypedEventBus.get().unregister(PieceSpawnedEvent.class, spawnListener);
        if (diedListener != null) TypedEventBus.get().unregister(PieceDiedEvent.class, diedListener);
        moveListener = null;
        spawnListener = null;
        diedListener = null;
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
        for (MonsterGamePiece prev : new HashSet<>(appliedTo)) {
            if (!now.contains(prev)) {
                prev.getStatsAccumulator().remove(mod);
                appliedTo.remove(prev);
            }
        }
        for (MonsterGamePiece target : now) {
            if (!appliedTo.contains(target)) {
                target.getStatsAccumulator().add(mod);
                appliedTo.add(target);
            }
        }
    }
}
