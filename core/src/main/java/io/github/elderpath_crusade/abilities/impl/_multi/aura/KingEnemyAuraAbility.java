package io.github.elderpath_crusade.abilities.impl._multi.aura;

import io.github.elderpath_crusade.abilities.AbilityType;
import io.github.elderpath_crusade.utils.AbilityUtils;
import io.github.elderpath_crusade.abilities.PassiveAbility;
import io.github.elderpath_crusade.abilities.stats.StatsModifier;
import io.github.elderpath_crusade.abilities.TriggeredAbility;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.GameEvent;
import io.github.elderpath_crusade.events.PieceDiedEvent;
import io.github.elderpath_crusade.events.PieceMovedEvent;
import io.github.elderpath_crusade.events.PieceSpawnedEvent;
import io.github.elderpath_crusade.events.TurnStartedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * King Enemy Aura: All enemy pieces within 1 range gain +1 action (max actions increased by 1).
 * On first entry into range (or starting turn in range), grant 1 action immediately.
 */
public class KingEnemyAuraAbility implements PassiveAbility, TriggeredAbility {
    @Override
    public AbilityType getType() { return AbilityType.PASSIVE; }
    private final StatsModifier mod;
    private MonsterGamePiece owner;
    private final Set<MonsterGamePiece> appliedTo = new HashSet<>();
    private final Set<MonsterGamePiece> grantedThisTurn = new HashSet<>();
    private Consumer<PieceMovedEvent> moveListener;
    private Consumer<PieceSpawnedEvent> spawnListener;
    private Consumer<PieceDiedEvent> diedListener;
    private Consumer<TurnStartedEvent> turnStartedListener;

    public KingEnemyAuraAbility() {
        this.mod = new StatsModifier();
        this.mod.source = this;
        this.mod.addActions = 1;
    }

    @Override
    public String getName() { return "King Enemy Aura"; }

    @Override
    public String getDescription() { return KingEnemyAuraAbility.getAbilityDescription(); }

    public static String getAbilityDescription() {
        return "Enemies within 1 range have +1 action";
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
        grantedThisTurn.clear();
        mod.clear();
        this.owner = null;
    }

    @Override
    public void onOwnerSpawned(MonsterGamePiece owner, int row, int col) {
        refreshRecipients(false);
    }

    @Override
    public void onOwnerMoved(MonsterGamePiece owner, int fromRow, int fromCol, int toRow, int toCol) {
        refreshRecipients(false);
    }

    @Override
    public void onOwnerDied(MonsterGamePiece owner) {
        onDetach();
    }

    @Override
    public void onTurnStarted(PieceAlignment currentPlayer) {
        grantedThisTurn.clear();
        refreshRecipients(true);
    }

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
        if (event instanceof TurnStartedEvent) {
            grantedThisTurn.clear();
            refreshRecipients(true);
        } else {
            refreshRecipients(false);
        }
    }

    private void registerGlobalListeners() {
        moveListener = e -> onGameEvent(e);
        spawnListener = e -> onGameEvent(e);
        diedListener = e -> onGameEvent(e);
        turnStartedListener = e -> onGameEvent(e);
        TypedEventBus.get().register(PieceMovedEvent.class, moveListener);
        TypedEventBus.get().register(PieceSpawnedEvent.class, spawnListener);
        TypedEventBus.get().register(PieceDiedEvent.class, diedListener);
        TypedEventBus.get().register(TurnStartedEvent.class, turnStartedListener);
    }

    private void unregisterGlobalListeners() {
        if (moveListener != null) TypedEventBus.get().unregister(PieceMovedEvent.class, moveListener);
        if (spawnListener != null) TypedEventBus.get().unregister(PieceSpawnedEvent.class, spawnListener);
        if (diedListener != null) TypedEventBus.get().unregister(PieceDiedEvent.class, diedListener);
        if (turnStartedListener != null) TypedEventBus.get().unregister(TurnStartedEvent.class, turnStartedListener);
        moveListener = null;
        spawnListener = null;
        diedListener = null;
        turnStartedListener = null;
    }

    private void refreshRecipients(boolean isStartOfTurn) {
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
        int r = pos.getRow();
        int c = pos.getCol();
        PieceAlignment align = owner.getAlignment();

        Set<MonsterGamePiece> now = new HashSet<>();
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int nr = r + dr;
                int nc = c + dc;
                if (nr < 0 || nr >= board.getROWS() || nc < 0 || nc >= board.getCOLS()) continue;
                GamePiece gp = board.getGamePieceAtPos(nr, nc);
                if (gp instanceof MonsterGamePiece mgp) {
                    if (mgp.getAlignment() != align) {
                        now.add(mgp);
                    }
                }
            }
        }

        for (MonsterGamePiece prev : new HashSet<>(appliedTo)) {
            if (!now.contains(prev)) {
                prev.getStatsAccumulator().remove(mod);
                appliedTo.remove(prev);
                grantedThisTurn.remove(prev);
            }
        }

        for (MonsterGamePiece target : now) {
            if (!appliedTo.contains(target)) {
                target.getStatsAccumulator().add(mod);
                appliedTo.add(target);
                if (!isStartOfTurn && !grantedThisTurn.contains(target)) {
                    int currentActions = AbilityUtils.getRemainingActions(target);
                    target.getStats().setRemainingActions(currentActions + 1);
                    grantedThisTurn.add(target);
                }
            }
        }
    }
}
