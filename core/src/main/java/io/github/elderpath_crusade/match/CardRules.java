package io.github.elderpath_crusade.match;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.abilities.data.Condition;
import io.github.elderpath_crusade.abilities.data.ConditionEvaluator;
import io.github.elderpath_crusade.abilities.data.EffectExecutor;
import io.github.elderpath_crusade.abilities.data.EffectNode;
import io.github.elderpath_crusade.abilities.data.ExpressionContext;
import io.github.elderpath_crusade.abilities.data.TargetSelector;
import io.github.elderpath_crusade.abilities.data.TargetSelectorResolver;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.SpellDefinition;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.ecs.factory.PieceFactory;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.CardPlayedEvent;
import io.github.elderpath_crusade.events.PieceSpawnedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game.PlayerManager;
import io.github.elderpath_crusade.utils.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Authoritative card-play rules: who may play which card from their hand, where, at what
 * cost, and what happens when they do. Works on pure state (HandState/DeckState, BoardGrid,
 * ECS mana) so it runs identically headlessly. Renderable cards only ask it whether to offer
 * a play (canPlay / isValid*Target) and send the play to GameServer.
 */
public class CardRules {
    private PieceSpawner pieceSpawner = PieceFactory::createPiece;

    public void setPieceSpawner(PieceSpawner pieceSpawner) {
        this.pieceSpawner = pieceSpawner;
    }

    /**
     * Whether to offer this card for play right now: it's alignment's turn and they can
     * afford it. Doesn't check the hand — a replica's renderable hand is the hand.
     */
    public boolean canPlay(PieceAlignment alignment, String cardName) {
        if (alignment == null || alignment != currentPlayer()) return false;
        int cost = CardCatalog.getCost(cardName);
        PlayerManager.PlayerState player = GameContext.get().getPlayerManager().get(alignment);
        return cost >= 0 && player != null && player.getMana() >= cost;
    }

    public boolean isValidSummonTarget(PieceAlignment alignment, int row, int col) {
        BoardGrid grid = GameContext.get().getActiveGrid();
        return grid != null && grid.isValidSummonTarget(row, col, alignment);
    }

    /** Whether (row, col) holds an entity that satisfies every targeting condition of this (targeted) spell. */
    public boolean isValidSpellTarget(PieceAlignment alignment, String cardName, int row, int col) {
        if (!(CardCatalog.get(cardName) instanceof CardCatalog.Spell spell)) return false;
        SpellDefinition definition = spell.definition();
        if (definition.targeting() == null) return false;
        BoardGrid grid = GameContext.get().getActiveGrid();
        if (grid == null || !grid.isInBounds(row, col)) return false;
        Entity candidate = grid.getEntityAt(row, col);
        if (candidate == null) return false;

        List<Condition> conditions = definition.targeting().conditions();
        if (conditions == null || conditions.isEmpty()) return true;

        ExpressionContext ctx = new ExpressionContext();
        ctx.set("$self.alignment", alignment.name());
        ctx.withTarget(Map.of(
                "health", EntityUtils.getCurrentHealth(candidate),
                "maxHealth", EntityUtils.getMaxHealth(candidate),
                "damage", EntityUtils.getDamage(candidate),
                "alignment", EntityUtils.getAlignment(candidate).name()
        ));
        for (Condition condition : conditions) {
            if (!ConditionEvaluator.evaluate(condition, ctx)) return false;
        }
        return true;
    }

    /**
     * Plays a summon card from alignment's hand onto (row, col): validates turn, hand, summon
     * row and mana, spawns the piece, and moves the card to the discard pile. Emits
     * PieceSpawnedEvent then CardPlayedEvent. Returns true on success.
     */
    public boolean playSummon(PieceAlignment alignment, String instanceId, int row, int col) {
        PlayerManager.PlayerState player = playerToAct(alignment);
        if (player == null) return false;
        CardInstance card = player.hand.find(instanceId);
        if (card == null || !(CardCatalog.get(card.cardName()) instanceof CardCatalog.Summon)) return false;
        PieceDefinition definition = CardCatalog.getPieceDefinition(card.cardName());
        if (definition == null) {
            Logger.error("CardRules", "No PieceDefinition for " + card.cardName());
            return false;
        }
        BoardGrid grid = GameContext.get().getActiveGrid();
        if (grid == null || !grid.isValidSummonTarget(row, col, alignment)) {
            Logger.log("CardRules", "Summon rejected: invalid target (" + row + "," + col + ")");
            return false;
        }
        if (!trySpendMana(player, definition.cost())) return false;

        Entity entity = pieceSpawner.spawn(definition, alignment, row, col);
        String pieceId = EntityUtils.getId(entity);
        grid.addEntity(row, col, entity);
        player.deck.playFromHand(instanceId);

        TypedEventBus.get().emit(new PieceSpawnedEvent(pieceId, alignment, row, col));
        TypedEventBus.get().emit(new CardPlayedEvent(card.cardName(), alignment, row, col, pieceId, instanceId));
        return true;
    }

    /**
     * Plays a spell card from alignment's hand: validates turn, hand, target (targetRow /
     * targetCol null for an untargeted spell) and mana, runs its effects, and moves the card
     * to the discard pile. Emits CardPlayedEvent after the effects. Returns true on success.
     */
    public boolean playSpell(PieceAlignment alignment, String instanceId, Integer targetRow, Integer targetCol) {
        PlayerManager.PlayerState player = playerToAct(alignment);
        if (player == null) return false;
        CardInstance card = player.hand.find(instanceId);
        if (card == null || !(CardCatalog.get(card.cardName()) instanceof CardCatalog.Spell spell)) return false;
        SpellDefinition definition = spell.definition();

        Entity target = null;
        if (definition.targeting() != null) {
            if (targetRow == null || targetCol == null) return false;
            if (!isValidSpellTarget(alignment, card.cardName(), targetRow, targetCol)) return false;
            target = GameContext.get().getActiveGrid().getEntityAt(targetRow, targetCol);
        }
        if (!trySpendMana(player, definition.manaCost())) return false;

        runSpellEffects(definition, alignment, target);
        player.deck.playFromHand(instanceId);

        int row = definition.targeting() != null ? targetRow : -1;
        int col = definition.targeting() != null ? targetCol : -1;
        TypedEventBus.get().emit(new CardPlayedEvent(card.cardName(), alignment, row, col, null, instanceId));
        return true;
    }

    /** alignment's player state if it's their turn and they have a deck, else null. */
    private PlayerManager.PlayerState playerToAct(PieceAlignment alignment) {
        if (alignment == null || alignment != currentPlayer()) return null;
        PlayerManager.PlayerState player = GameContext.get().getPlayerManager().get(alignment);
        return (player == null || player.deck == null) ? null : player;
    }

    private boolean trySpendMana(PlayerManager.PlayerState player, int cost) {
        if (player.getMana() < cost) {
            Logger.log("CardRules", "Not enough mana. Need=" + cost + ", have=" + player.getMana());
            return false;
        }
        player.addMana(-cost);
        return true;
    }

    private void runSpellEffects(SpellDefinition definition, PieceAlignment caster, Entity chosen) {
        ExpressionContext ctx = new ExpressionContext();
        ctx.set("$caster.alignment", caster.name());
        Map<String, Object> state = new HashMap<>();
        for (EffectNode effectNode : definition.effects()) {
            List<Entity> targets = resolveTopLevelTarget(effectNode, chosen, ctx);
            EffectExecutor.execute(effectNode, targets, null, ctx, state);
        }
    }

    /**
     * Resolves a top-level effect's "target" param the same way
     * ActionableAbilityExecutor/AbilityResolverSystem do for abilities. Effects with no
     * "target" param (DrawCard, Branch, ForEach, Recast, ...) resolve their own operands
     * internally and ignore the returned list.
     */
    private List<Entity> resolveTopLevelTarget(EffectNode effect, Entity chosen, ExpressionContext context) {
        Object targetParam = effect.params().get("target");
        if (targetParam instanceof String s) {
            if ("$chosen".equals(s)) return chosen != null ? List.of(chosen) : List.of();
            return TargetSelectorResolver.resolve(new TargetSelector(s), null, context);
        }
        return List.of();
    }

    private PieceAlignment currentPlayer() {
        return GameContext.get().getTurnManager().getCurrentPlayer();
    }
}
