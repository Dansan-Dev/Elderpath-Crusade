package io.github.elderpath_crusade.abilities.impl;

import io.github.elderpath_crusade.abilities.AbilityType;
import io.github.elderpath_crusade.abilities.AbilityUtils;
import io.github.elderpath_crusade.abilities.PassiveAbility;
import io.github.elderpath_crusade.abilities.stats.StatsModifier;
import io.github.elderpath_crusade.abilities.TriggeredAbility;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.multiplayer.EventBus;
import io.github.elderpath_crusade.multiplayer.GameEvent;
import io.github.elderpath_crusade.multiplayer.GameEventType;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * King Enemy Aura: All enemy pieces within 1 range gain +1 action (max actions increased by 1).
 * On first entry into range (or starting turn in range), grant 1 action immediately.
 * Tracks which enemies have been granted the action bonus this turn to prevent multiple grants.
 */
public class KingEnemyAuraAbility implements PassiveAbility, TriggeredAbility {
    @Override
    public AbilityType getType() { return AbilityType.PASSIVE; }
    private final StatsModifier mod;
    private MonsterGamePiece owner;
    private final Set<MonsterGamePiece> appliedTo = new HashSet<>();
    private final Set<MonsterGamePiece> grantedThisTurn = new HashSet<>(); // Track pieces granted action this turn
    // Event listeners for global re-evaluation
    private Consumer<GameEvent> moveListener;
    private Consumer<GameEvent> spawnListener;
    private Consumer<GameEvent> diedListener;
    private Consumer<GameEvent> turnStartedListener;

    public KingEnemyAuraAbility() {
        this.mod = new StatsModifier();
        this.mod.source = this;
        this.mod.addActions = 1; // Increase max actions by 1
    }

    @Override
    public String getName() { return "King Enemy Aura"; }

    @Override
    public String getDescription() { return KingEnemyAuraAbility.getAbilityDescription(); }

    public static String getAbilityDescription() {
        return "Enemies within 1 range\nhave +1 action";
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
        // Don't refresh recipients here - position may not be set yet
        // It will be refreshed when the piece is spawned via onOwnerSpawned()
    }

    @Override
    public void onDetach() {
        unregisterGlobalListeners();
        // Remove the modifier from all recipients before clearing
        for (MonsterGamePiece target : new HashSet<>(appliedTo)) {
            target.getStatsAccumulator().remove(mod);
        }
        appliedTo.clear();
        grantedThisTurn.clear();
        // Clear the modifier (this also removes it from all holders)
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
        // Clear tracking set on turn start
        grantedThisTurn.clear();
        // Refresh recipients - pieces starting turn in range will get +1 action via increased max actions
        refreshRecipients(true);
    }

    @Override
    public void onGameEvent(GameEvent event) {
        if (owner == null) return;
        // Check if owner is dead (no position data)
        Object ownerPosObj = owner.getData(GamePieceData.POSITION);
        if (!(ownerPosObj instanceof Board.Position)) {
            // Owner is dead, detach
            onDetach();
            return;
        }
        GameEventType t = event.getType();
        // Check if this is the King dying
        if (t == GameEventType.PIECE_DIED) {
            Object pieceIdObj = event.getData().get("pieceId");
            if (pieceIdObj != null && owner != null && pieceIdObj.toString().equals(owner.getId().toString())) {
                // King died, detach
                onDetach();
                return;
            }
        }
        // Re-evaluate when any piece moves/spawns/dies on same board to keep range correct
        if (t == GameEventType.ACTIVE_MOVEMENT || t == GameEventType.FORCED_MOVEMENT
            || t == GameEventType.PIECE_SPAWNED || t == GameEventType.PIECE_DIED) {
            refreshRecipients(false);
        }

        if (t == GameEventType.TURN_STARTED) {
            refreshRecipients(true);
        }
    }

    private void registerGlobalListeners() {
        moveListener = this::onGameEvent;
        spawnListener = this::onGameEvent;
        diedListener = this::onGameEvent;
        turnStartedListener = this::onGameEvent;
        EventBus.register(GameEventType.ACTIVE_MOVEMENT, moveListener);
        EventBus.register(GameEventType.FORCED_MOVEMENT, moveListener);
        EventBus.register(GameEventType.PIECE_SPAWNED, spawnListener);
        EventBus.register(GameEventType.PIECE_DIED, diedListener);
        EventBus.register(GameEventType.TURN_STARTED, turnStartedListener);
    }

    private void unregisterGlobalListeners() {
        if (moveListener != null) {
            EventBus.unregister(GameEventType.ACTIVE_MOVEMENT, moveListener);
            EventBus.unregister(GameEventType.FORCED_MOVEMENT, moveListener);
        }
        if (spawnListener != null) EventBus.unregister(GameEventType.PIECE_SPAWNED, spawnListener);
        if (diedListener != null) EventBus.unregister(GameEventType.PIECE_DIED, diedListener);
        if (turnStartedListener != null) EventBus.unregister(GameEventType.TURN_STARTED, turnStartedListener);
        moveListener = spawnListener = diedListener = turnStartedListener = null;
    }

    private void refreshRecipients(boolean isStartOfTurn) {
        if (owner == null) return;
        // Check if owner has position data (might not be set during initial attach)
        Object posObj = owner.getData(GamePieceData.POSITION);
        if (!(posObj instanceof Board.Position pos)) {
            // Position not set yet (piece not spawned) or owner is dead
            // If owner has no position and we've already applied to someone, detach (dead)
            // Otherwise, just return early (not spawned yet)
            if (!appliedTo.isEmpty()) {
                // We had recipients before, so owner must have died
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

        // Find all enemies within Chebyshev distance 1 (9 squares: center + 8 surrounding)
        Set<MonsterGamePiece> now = new HashSet<>();
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int nr = r + dr;
                int nc = c + dc;
                if (nr < 0 || nr >= board.getROWS() || nc < 0 || nc >= board.getCOLS()) continue;
                GamePiece gp = board.getGamePieceAtPos(nr, nc);
                if (gp instanceof MonsterGamePiece mgp) {
                    // Apply to enemies within range (opposite alignment)
                    if (mgp.getAlignment() != align) {
                        now.add(mgp);
                    }
                }
            }
        }
        
        // Remove from pieces no longer eligible
        for (MonsterGamePiece prev : new HashSet<>(appliedTo)) {
            if (!now.contains(prev)) {
                prev.getStatsAccumulator().remove(mod);
                appliedTo.remove(prev);
                grantedThisTurn.remove(prev); // Also remove from tracking if out of range
            }
        }
        
        // Add to new recipients
        for (MonsterGamePiece target : now) {
            if (!appliedTo.contains(target)) {
                // First time entering range: add modifier and grant 1 action immediately (only if not at turn start)
                target.getStatsAccumulator().add(mod);
                appliedTo.add(target);
                // Only grant action if NOT at turn start (turn start actions are handled by resetActionsForOwner + max actions boost)
                if (!isStartOfTurn && !grantedThisTurn.contains(target)) {
                    // Grant 1 action immediately (increment remaining actions) for mid-turn entry
                    int currentActions = AbilityUtils.getRemainingActions(target);
                    target.getStats().setRemainingActions(currentActions + 1);
                    grantedThisTurn.add(target);
                }
            }
            // Note: Pieces that start turn in range don't get extra action here - they benefit from:
            // 1. Max actions boost from modifier (handled by effectiveActions)
            // 2. Actions reset via resetActionsForOwner (which uses effectiveActions)
        }


    }
}

