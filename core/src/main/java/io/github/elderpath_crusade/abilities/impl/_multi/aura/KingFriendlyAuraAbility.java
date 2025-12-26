package io.github.elderpath_crusade.abilities.impl._multi.aura;

import io.github.elderpath_crusade.abilities.AbilityType;
import io.github.elderpath_crusade.abilities.PassiveAbility;
import io.github.elderpath_crusade.abilities.stats.StatsModifier;
import io.github.elderpath_crusade.abilities.TriggeredAbility;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.game_piece.GamePiece;
import io.github.elderpath_crusade.game_objects.board.game_piece.MonsterGamePiece;
import io.github.elderpath_crusade.multiplayer.EventBus;
import io.github.elderpath_crusade.multiplayer.GameEvent;
import io.github.elderpath_crusade.multiplayer.GameEventType;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * King Friendly Aura: All other friendly pieces gain +1 max health.
 * Similar pattern to CommanderAuraAbility but grants health instead of attack.
 */
public class KingFriendlyAuraAbility implements PassiveAbility, TriggeredAbility {
    @Override
    public AbilityType getType() { return AbilityType.PASSIVE; }
    private final StatsModifier mod;
    private MonsterGamePiece owner;
    private final Set<MonsterGamePiece> appliedTo = new HashSet<>();
    // Event listeners for global re-evaluation
    private Consumer<GameEvent> moveListener;
    private Consumer<GameEvent> spawnListener;
    private Consumer<GameEvent> diedListener;

    public KingFriendlyAuraAbility() {
        this.mod = new StatsModifier();
        this.mod.source = this;
        this.mod.addMaxHealth = 1; // Increase max health by 1
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
        // Clear the modifier (this also removes it from all holders)
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
        // Re-evaluate when any piece moves/spawns/dies on same board
        if (t == GameEventType.PIECE_SPAWNED || t == GameEventType.PIECE_DIED) {
            refreshRecipients();
        }
    }

    private void registerGlobalListeners() {
        spawnListener = this::onGameEvent;
        diedListener = this::onGameEvent;
        EventBus.register(GameEventType.PIECE_SPAWNED, spawnListener);
        EventBus.register(GameEventType.PIECE_DIED, diedListener);
    }

    private void unregisterGlobalListeners() {
        if (spawnListener != null) EventBus.unregister(GameEventType.PIECE_SPAWNED, spawnListener);
        if (diedListener != null) EventBus.unregister(GameEventType.PIECE_DIED, diedListener);
        spawnListener = diedListener = null;
    }

    private void refreshRecipients() {
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
        PieceAlignment align = owner.getAlignment();

        // Find all friendly pieces on the board (excluding self)
        Set<MonsterGamePiece> now = new HashSet<>();
        for (int r = 0; r < board.getROWS(); r++) {
            for (int c = 0; c < board.getCOLS(); c++) {
                GamePiece gp = board.getGamePieceAtPos(r, c);
                if (gp instanceof MonsterGamePiece mgp) {
                    // Apply to other friendly units (excluding self)
                    if (mgp.getAlignment() == align && mgp != owner) {
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
            }
        }

        // Add to new recipients
        for (MonsterGamePiece target : now) {
            if (!appliedTo.contains(target)) {
                target.getStatsAccumulator().add(mod);
                appliedTo.add(target);
                // Heal by 1 when gaining the max health buff (to match the new max health)
                if (target.getStats().getCurrentHealth() < target.getEffectiveMaxHealth()) {
                    target.heal(1);
                }
            }
        }
    }
}

