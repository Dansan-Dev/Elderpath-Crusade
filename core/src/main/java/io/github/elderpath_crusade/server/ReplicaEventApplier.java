package io.github.elderpath_crusade.server;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.ecs.components.ComputedStatsComponent;
import io.github.elderpath_crusade.ecs.factory.PieceFactory;
import io.github.elderpath_crusade.ecs.systems.PlayerSystem;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.*;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.cards.Card;
import io.github.elderpath_crusade.game_objects.cards.HandSync;
import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.match.CardInstance;
import io.github.elderpath_crusade.match.HandState;
import io.github.elderpath_crusade.multiplayer.net.GameSnapshot;
import io.github.elderpath_crusade.utils.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Applies the host's relayed GameEvents directly to this (guest) process's own ECS/board/HUD
 * state, WITHOUT running any gameplay ECS system (AbilityResolverSystem, CombatSystem,
 * MovementSystem, AttackSystem, DeathSystem, PassiveModifierSystem) — those already ran, once,
 * correctly, on the host; their own result events are already part of the relayed stream.
 * This class only ever mirrors, never decides.
 *
 * Mirrors BOTH hands from CardDrawnEvent/CardPlayedEvent/CardDiscardedEvent by card instance
 * id — into this process's PlayerState hand (pure state) and into the hand views via
 * HandSync. The local player's own hand is shown front-up and the opponent's face-down (real
 * cards are reconstructed there too — safe, since Card only renders front-specific content
 * while faceUp — so the opponent's hand shows the right card *count*).
 *
 * A guest joining after the match has already started has missed everything that happened
 * before it connected — applySnapshot() (driven by a one-time GameSnapshot the host sends
 * right after accepting the connection, see GameHost/GameSnapshot) bootstraps existing board
 * pieces, both hands, mana, and whose turn it is, before any further incremental event is
 * processed.
 */
public class ReplicaEventApplier {
    private final Map<String, int[]> pendingSpawns = new HashMap<>(); // pieceId -> [row, col]
    private final Map<String, Entity> entitiesById = new HashMap<>();
    private final HandSync handSyncP1;
    private final HandSync handSyncP2;

    public ReplicaEventApplier(HandSync handSyncP1, HandSync handSyncP2) {
        this.handSyncP1 = handSyncP1;
        this.handSyncP2 = handSyncP2;
    }

    public void apply(GameEvent event) {
        try {
            if (event instanceof PieceSpawnedEvent e) onPieceSpawned(e);
            else if (event instanceof CardPlayedEvent e) onCardPlayed(e);
            else if (event instanceof CardDrawnEvent e) onCardDrawn(e);
            else if (event instanceof CardDiscardedEvent e) onCardDiscarded(e);
            else if (event instanceof PieceMovedEvent e) onPieceMoved(e);
            else if (event instanceof PieceAttackedEvent e) applyDamageAt(e.defenderRow(), e.defenderCol(), e.damage());
            else if (event instanceof PieceDamagedEvent e) applyDamageAt(e.row(), e.col(), e.amount());
            else if (event instanceof PieceHealedEvent e) onPieceHealed(e);
            else if (event instanceof PieceDiedEvent e) onPieceDied(e);
            else if (event instanceof ManaChangedEvent e) onManaChanged(e);
            else if (event instanceof ActionSpentEvent e) onActionSpent(e);
            else if (event instanceof ActionsResetEvent e) onActionsReset(e);
            else if (event instanceof TurnStartedEvent e) onTurnStarted(e);
            else if (event instanceof GameWonEvent e) GameContext.get().getWinConditionManager().onGameWon(e);
            // TurnEndedEvent, PieceKilledEvent, CardShuffledEvent: no direct board/HUD
            // mutation needed on the replica side.
        } catch (Exception ex) {
            Logger.error("ReplicaEventApplier", "Failed to apply " + event + ": " + ex.getMessage());
        }
    }

    // --- Board state ---

    private void onPieceSpawned(PieceSpawnedEvent e) {
        pendingSpawns.put(e.pieceId(), new int[]{e.row(), e.col()});
    }

    private void onCardPlayed(CardPlayedEvent e) {
        handState(e.owner()).remove(e.instanceId());
        HandSync sync = handSyncOf(e.owner());
        if (sync != null) sync.onCardPlayed(e);
        int[] pos = e.pieceId() == null ? null : pendingSpawns.remove(e.pieceId());
        if (pos == null) return; // a spell, not a summon — nothing to spawn on the board
        String registryKey = CardCatalog.getRegistryKeyForSummon(e.cardName());
        if (registryKey == null) return;
        PieceDefinition def = PieceRegistry.get(registryKey);
        Board board = GameContext.get().getActiveBoard();
        if (def == null || board == null) return;
        Entity entity = PieceFactory.createPiece(def, 0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(),
                e.owner(), pos[0], pos[1]);
        board.addEntityToPos(pos[0], pos[1], entity, e.pieceId());
        entitiesById.put(e.pieceId(), entity);
    }

    private void onActionSpent(ActionSpentEvent e) {
        Entity entity = entitiesById.get(e.pieceId());
        if (entity == null) return;
        StatsComponent stats = entity.getComponent(StatsComponent.class);
        if (stats != null) stats.remainingActions = e.remaining();
    }

    private void onPieceMoved(PieceMovedEvent e) {
        Board board = GameContext.get().getActiveBoard();
        if (board == null) return;
        Entity entity = board.getEntityAtPos(e.fromRow(), e.fromCol());
        if (entity == null) return;
        PositionComponent pos = entity.getComponent(PositionComponent.class);
        if (pos != null) pos.set(e.toRow(), e.toCol());
        board.moveEntity(e.fromRow(), e.fromCol(), entity, e.toRow(), e.toCol());
    }

    private void applyDamageAt(int row, int col, int amount) {
        Board board = GameContext.get().getActiveBoard();
        if (board == null) return;
        Entity entity = board.getEntityAtPos(row, col);
        if (entity == null) return;
        StatsComponent stats = entity.getComponent(StatsComponent.class);
        if (stats != null) stats.currentHealth -= amount;
    }

    private void onPieceHealed(PieceHealedEvent e) {
        Board board = GameContext.get().getActiveBoard();
        if (board == null) return;
        Entity entity = board.getEntityAtPos(e.row(), e.col());
        if (entity == null) return;
        StatsComponent stats = entity.getComponent(StatsComponent.class);
        if (stats != null) stats.currentHealth = Math.min(stats.maxHealth, stats.currentHealth + e.amount());
    }

    private void onPieceDied(PieceDiedEvent e) {
        entitiesById.remove(e.pieceId());
        Board board = GameContext.get().getActiveBoard();
        if (board != null) board.removeEntityAtPos(e.row(), e.col());
    }

    private void onManaChanged(ManaChangedEvent e) {
        GameContext.get().getEcsEngine().getSystem(PlayerSystem.class).setMana(e.player(), e.newMana());
    }

    private void onActionsReset(ActionsResetEvent e) {
        Board board = GameContext.get().getActiveBoard();
        if (board == null) return;
        for (int row = 0; row < board.getROWS(); row++) {
            for (int col = 0; col < board.getCOLS(); col++) {
                Entity entity = board.getEntityAtPos(row, col);
                if (entity == null) continue;
                var align = io.github.elderpath_crusade.ecs.EntityUtils.getAlignment(entity);
                if (align != e.player()) continue;
                StatsComponent stats = entity.getComponent(StatsComponent.class);
                ComputedStatsComponent computed = entity.getComponent(ComputedStatsComponent.class);
                if (stats != null) stats.remainingActions = (computed != null) ? computed.actions : stats.actions;
            }
        }
    }

    private void onTurnStarted(TurnStartedEvent e) {
        GameContext.get().getTurnManager().setCurrentPlayerForReplica(e.player());
    }

    // --- Hand mirroring (both alignments — own hand front-up, opponent's face-down) ---

    private void onCardDrawn(CardDrawnEvent e) {
        addCardToHand(e.owner(), new CardInstance(e.instanceId(), e.cardName()));
    }

    private void onCardDiscarded(CardDiscardedEvent e) {
        if (e.instanceIds() != null) {
            for (String instanceId : e.instanceIds()) handState(e.player()).remove(instanceId);
        }
        HandSync sync = handSyncOf(e.player());
        if (sync != null) sync.onCardDiscarded(e);
    }

    private void addCardToHand(PieceAlignment owner, CardInstance card) {
        PieceAlignment local = GameContext.get().getOnlineMatch().getLocalAlignment();
        handState(owner).add(card);
        HandSync sync = handSyncOf(owner);
        if (local == null || sync == null) return;
        Card view = sync.addCard(card);
        if (view == null) return;
        if (owner == local) view.showFront(); else view.showBack();
    }

    private HandState handState(PieceAlignment alignment) {
        return GameContext.get().getPlayerManager().get(alignment).hand;
    }

    private HandSync handSyncOf(PieceAlignment alignment) {
        return alignment == PieceAlignment.P1 ? handSyncP1 : handSyncP2;
    }

    // --- Late-join catch-up ---

    /** Bootstraps this guest to the host's current state — see GameSnapshot's own doc. */
    public void applySnapshot(GameSnapshot snapshot) {
        Board board = GameContext.get().getActiveBoard();
        if (board == null) return;

        for (GameSnapshot.PieceState p : snapshot.pieces()) {
            PieceDefinition def = PieceRegistry.get(p.registryKey());
            if (def == null) continue;
            Entity entity = PieceFactory.createPiece(def, 0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(),
                    p.owner(), p.row(), p.col());
            StatsComponent stats = entity.getComponent(StatsComponent.class);
            if (stats != null) {
                stats.currentHealth = p.currentHealth();
                stats.remainingActions = p.remainingActions();
            }
            board.addEntityToPos(p.row(), p.col(), entity, p.pieceId());
            entitiesById.put(p.pieceId(), entity);
        }

        for (CardInstance card : snapshot.p1Hand()) addCardToHand(PieceAlignment.P1, card);
        for (CardInstance card : snapshot.p2Hand()) addCardToHand(PieceAlignment.P2, card);

        PlayerSystem playerSystem = GameContext.get().getEcsEngine().getSystem(PlayerSystem.class);
        playerSystem.setMana(PieceAlignment.P1, snapshot.p1Mana());
        playerSystem.setMana(PieceAlignment.P2, snapshot.p2Mana());

        GameContext.get().getTurnManager().setCurrentPlayerForReplica(snapshot.currentPlayer());
    }
}
