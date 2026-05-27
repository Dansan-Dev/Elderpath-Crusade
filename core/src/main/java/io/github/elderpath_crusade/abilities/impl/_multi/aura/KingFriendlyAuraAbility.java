package io.github.elderpath_crusade.abilities.impl._multi.aura;

import io.github.elderpath_crusade.abilities.AbilityType;
import io.github.elderpath_crusade.abilities.PassiveAbility;
import io.github.elderpath_crusade.abilities.stats.StatsModifier;
import io.github.elderpath_crusade.abilities.TriggeredAbility;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.GameEvent;
import io.github.elderpath_crusade.events.PieceDiedEvent;
import io.github.elderpath_crusade.events.PieceSpawnedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * King Friendly Aura: All other friendly pieces gain +1 max health.
 */
public class KingFriendlyAuraAbility implements PassiveAbility, TriggeredAbility {
    @Override
    public AbilityType getType() { return AbilityType.PASSIVE; }
    private final StatsModifier mod;
    private MonsterGamePiece owner;
    private final Set<MonsterGamePiece> appliedTo = new HashSet<>();
    private Consumer<PieceSpawnedEvent> spawnListener;
    private Consumer<PieceDiedEvent> diedListener;

    public KingFriendlyAuraAbility() {
        this.mod = new StatsModifier();
        this.mod.source = this;
        this.mod.addMaxHealth = 1;
    }

    @Override
    public String getName() { return "King Friendly Aura"; }

    @Override
    public String getDescription() { return KingFriendlyAuraAbility.getAbilityDescription(); }

    public static String getAbilityDescription() {
        return "Other friendly units have +1 health";
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
    }

    @Override
    public void onDetach() {
        unregisterGlobalListeners();
        for (MonsterGamePiece target : new HashSet<>(appliedTo)) {
            target.getStatsAccumulator().remove(mod);
        }
        appliedTo.clear();
        mod.clear();
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
        Object ownerPosObj = owner.getData(GamePieceData.POSITION);
        if (!(ownerPosObj instanceof Board.Position)) {
            onDetach();
            return;
        }
        if (event instanceof PieceDiedEvent died) {
            if (owner != null && died.pieceId().equals(owner.getId().toString())) {
                onDetach();
                return;
            }
        }
        refreshRecipients();
    }

    private void registerGlobalListeners() {
        spawnListener = e -> onGameEvent(e);
        diedListener = e -> onGameEvent(e);
        TypedEventBus.get().register(PieceSpawnedEvent.class, spawnListener);
        TypedEventBus.get().register(PieceDiedEvent.class, diedListener);
    }

    private void unregisterGlobalListeners() {
        if (spawnListener != null) TypedEventBus.get().unregister(PieceSpawnedEvent.class, spawnListener);
        if (diedListener != null) TypedEventBus.get().unregister(PieceDiedEvent.class, diedListener);
        spawnListener = null;
        diedListener = null;
    }

    private void refreshRecipients() {
        if (owner == null) return;
        Object posObj = owner.getData(GamePieceData.POSITION);
        if (!(posObj instanceof Board.Position pos)) {
            if (!appliedTo.isEmpty()) {
                onDetach();
            }
            return;
        }
        Board board = pos.getBoard();
        if (board == null) {
            onDetach();
            return;
        }
        PieceAlignment align = owner.getAlignment();

        Set<MonsterGamePiece> now = new HashSet<>();
        for (int r = 0; r < board.getROWS(); r++) {
            for (int c = 0; c < board.getCOLS(); c++) {
                GamePiece gp = board.getGamePieceAtPos(r, c);
                if (gp instanceof MonsterGamePiece mgp) {
                    if (mgp.getAlignment() == align && mgp != owner) {
                        now.add(mgp);
                    }
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
                if (target.getStats().getCurrentHealth() < target.getEffectiveMaxHealth()) {
                    target.heal(1);
                }
            }
        }
    }
}
