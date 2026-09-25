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
import io.github.elderpath_crusade.game.DeckManager;
import io.github.elderpath_crusade.game.PlayerManager;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.cards.Card;
import io.github.elderpath_crusade.game_objects.cards.CardFactory;
import io.github.elderpath_crusade.game_objects.cards.Hand;
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
 * Also reconstructs the guest's OWN hand (not the opponent's — out of scope, see below) from
 * CardDrawnEvent/CardPlayedEvent/CardDiscardedEvent, in the same order the host draws them —
 * this isn't just cosmetic: PlaySummonCard/PlaySpellCard commands reference a card by hand
 * index, so the guest's hand ordering must exactly match the host's for those indices to mean
 * the same card on both sides.
 *
 * Known limitation: the opponent's (host's) hand is not reconstructed on the guest's screen —
 * only the guest's own hand. Watching an opponent's face-down card count is cosmetic and not
 * required for the match to be playable.
 */
public class ReplicaEventApplier {
    private final Map<String, int[]> pendingSpawns = new HashMap<>(); // pieceId -> [row, col]
    private final Map<String, Entity> entitiesById = new HashMap<>();

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
        int[] pos = pendingSpawns.remove(e.pieceId());
        removeFirstFromLocalHand(e.owner(), e.cardName());
        if (pos == null) return; // a spell, not a summon — nothing to spawn on the board
        String registryKey = CardFactory.getRegistryKeyForSummon(e.cardName());
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

    // --- Own hand reconstruction ---

    private void onCardDrawn(CardDrawnEvent e) {
        PieceAlignment local = GameContext.get().getOnlineMatch().getLocalAlignment();
        if (local == null || e.owner() != local) return;
        PlayerManager.PlayerState playerState = GameContext.get().getPlayerManager().get(local);
        Hand hand = playerState == null ? null : playerState.hand;
        Board board = GameContext.get().getActiveBoard();
        if (hand == null || board == null) return;
        try {
            DeckManager.CardCreationParams params = new DeckManager.CardCreationParams(
                    board, local, 0, 0, hand.getCardWidth(), hand.getCardHeight(), 0);
            Card card = CardFactory.create(e.cardName(), params);
            card.showFront(); // this is always the local player's own card
            hand.addCard(card);
        } catch (IllegalArgumentException ex) {
            Logger.error("ReplicaEventApplier", "Unknown card in CardDrawnEvent: " + e.cardName());
        }
    }

    private void onCardDiscarded(CardDiscardedEvent e) {
        PieceAlignment local = GameContext.get().getOnlineMatch().getLocalAlignment();
        if (local == null || e.player() != local) return;
        PlayerManager.PlayerState playerState = GameContext.get().getPlayerManager().get(local);
        Hand hand = playerState == null ? null : playerState.hand;
        if (hand == null) return;
        for (Card c : new java.util.ArrayList<>(hand.getCards())) {
            hand.removeCard(c);
        }
        hand.updateBounds();
    }

    private void removeFirstFromLocalHand(PieceAlignment owner, String cardName) {
        PieceAlignment local = GameContext.get().getOnlineMatch().getLocalAlignment();
        if (local == null || owner != local) return;
        PlayerManager.PlayerState playerState = GameContext.get().getPlayerManager().get(local);
        Hand hand = playerState == null ? null : playerState.hand;
        if (hand == null) return;
        for (Card c : hand.getCards()) {
            if (cardName.equals(c.getDisplayName())) {
                hand.removeCard(c);
                hand.updateBounds();
                return;
            }
        }
    }
}
