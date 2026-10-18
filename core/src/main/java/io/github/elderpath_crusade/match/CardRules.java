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
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.ecs.factory.PieceFactory;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.CardPlayedEvent;
import io.github.elderpath_crusade.events.PieceSpawnedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game.PlayerManager;
import io.github.elderpath_crusade.match.ActionResult.Reason;
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
        if (candidate == null || candidate.getComponent(StatsComponent.class) == null) return false; // pieces only, not terrain

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

    /** Whether actor may play summon card instanceId onto (row, col) right now — every check playSummon makes, without playing it. */
    public ActionResult validateSummon(PieceAlignment actor, String instanceId, int row, int col) {
        return checkSummon(actor, instanceId, row, col).result();
    }

    /**
     * Plays a summon card from actor's hand onto (row, col): validates turn, that the card is
     * in actor's own hand, summon row and mana, spawns the piece, and moves the card to the
     * discard pile. Emits PieceSpawnedEvent then CardPlayedEvent.
     */
    public ActionResult playSummon(PieceAlignment actor, String instanceId, int row, int col) {
        SummonCheck check = checkSummon(actor, instanceId, row, col);
        if (!check.result().isOk()) {
            if (check.result().reason() == Reason.INVALID_SUMMON_TARGET) {
                Logger.log("CardRules", "Summon rejected: invalid target (" + row + "," + col + ")");
            }
            return check.result();
        }
        if (!trySpendMana(check.player(), check.definition().cost())) return ActionResult.rejected(Reason.NOT_ENOUGH_MANA);

        BoardGrid grid = GameContext.get().getActiveGrid();
        Entity entity = pieceSpawner.spawn(check.definition(), actor, row, col);
        String pieceId = EntityUtils.getId(entity);
        grid.addEntity(row, col, entity);
        check.player().deck.playFromHand(instanceId);

        TypedEventBus.get().emit(new PieceSpawnedEvent(pieceId, actor, row, col, EntityUtils.getName(entity)));
        TypedEventBus.get().emit(new CardPlayedEvent(check.card().cardName(), actor, row, col, pieceId, instanceId));
        return ActionResult.ok();
    }

    private record SummonCheck(ActionResult result, PlayerManager.PlayerState player, CardInstance card,
                               PieceDefinition definition) {
        static SummonCheck rejected(Reason reason) {
            return new SummonCheck(ActionResult.rejected(reason), null, null, null);
        }
    }

    private SummonCheck checkSummon(PieceAlignment actor, String instanceId, int row, int col) {
        BoardGrid grid = GameContext.get().getActiveGrid();
        if (grid == null) return SummonCheck.rejected(Reason.NO_ACTIVE_MATCH);
        PlayerManager.PlayerState player = playerToAct(actor);
        if (player == null) return SummonCheck.rejected(Reason.NOT_YOUR_TURN);
        CardInstance card = player.hand.find(instanceId);
        if (card == null) return SummonCheck.rejected(Reason.CARD_NOT_IN_HAND);
        if (!(CardCatalog.get(card.cardName()) instanceof CardCatalog.Summon)) return SummonCheck.rejected(Reason.WRONG_CARD_TYPE);
        PieceDefinition definition = CardCatalog.getPieceDefinition(card.cardName());
        if (definition == null) {
            Logger.error("CardRules", "No PieceDefinition for " + card.cardName());
            return SummonCheck.rejected(Reason.UNKNOWN_CARD);
        }
        if (!grid.isValidSummonTarget(row, col, actor)) return SummonCheck.rejected(Reason.INVALID_SUMMON_TARGET);
        if (player.getMana() < definition.cost()) return SummonCheck.rejected(Reason.NOT_ENOUGH_MANA);
        return new SummonCheck(ActionResult.ok(), player, card, definition);
    }

    /**
     * Whether actor may play spell card instanceId at (targetRow, targetCol) — null/null for an
     * untargeted spell — right now: every check playSpell makes, without casting it.
     */
    public ActionResult validateSpell(PieceAlignment actor, String instanceId, Integer targetRow, Integer targetCol) {
        return checkSpell(actor, instanceId, targetRow, targetCol).result();
    }

    /**
     * Plays a spell card from actor's hand: validates turn, that the card is in actor's own
     * hand, target (targetRow / targetCol null for an untargeted spell) and mana, runs its
     * effects, and moves the card to the discard pile. Emits CardPlayedEvent after the effects.
     */
    public ActionResult playSpell(PieceAlignment actor, String instanceId, Integer targetRow, Integer targetCol) {
        SpellCheck check = checkSpell(actor, instanceId, targetRow, targetCol);
        if (!check.result().isOk()) return check.result();
        SpellDefinition definition = check.definition();
        if (!trySpendMana(check.player(), definition.manaCost())) return ActionResult.rejected(Reason.NOT_ENOUGH_MANA);

        runSpellEffects(definition, actor, check.target());
        check.player().deck.playFromHand(instanceId);

        int row = definition.targeting() != null ? targetRow : -1;
        int col = definition.targeting() != null ? targetCol : -1;
        TypedEventBus.get().emit(new CardPlayedEvent(check.card().cardName(), actor, row, col, null, instanceId));
        return ActionResult.ok();
    }

    private record SpellCheck(ActionResult result, PlayerManager.PlayerState player, CardInstance card,
                              SpellDefinition definition, Entity target) {
        static SpellCheck rejected(Reason reason) {
            return new SpellCheck(ActionResult.rejected(reason), null, null, null, null);
        }
    }

    private SpellCheck checkSpell(PieceAlignment actor, String instanceId, Integer targetRow, Integer targetCol) {
        if (GameContext.get().getActiveGrid() == null) return SpellCheck.rejected(Reason.NO_ACTIVE_MATCH);
        PlayerManager.PlayerState player = playerToAct(actor);
        if (player == null) return SpellCheck.rejected(Reason.NOT_YOUR_TURN);
        CardInstance card = player.hand.find(instanceId);
        if (card == null) return SpellCheck.rejected(Reason.CARD_NOT_IN_HAND);
        if (!(CardCatalog.get(card.cardName()) instanceof CardCatalog.Spell spell)) return SpellCheck.rejected(Reason.WRONG_CARD_TYPE);
        SpellDefinition definition = spell.definition();

        Entity target = null;
        if (definition.targeting() == null) {
            // untargeted: a target given anyway isn't a legal play (LegalActions lists it as untargeted only)
            if (targetRow != null || targetCol != null) return SpellCheck.rejected(Reason.INVALID_SPELL_TARGET);
        } else {
            if (targetRow == null || targetCol == null
                    || !isValidSpellTarget(actor, card.cardName(), targetRow, targetCol)) {
                return SpellCheck.rejected(Reason.INVALID_SPELL_TARGET);
            }
            target = GameContext.get().getActiveGrid().getEntityAt(targetRow, targetCol);
        }
        if (player.getMana() < definition.manaCost()) return SpellCheck.rejected(Reason.NOT_ENOUGH_MANA);
        return new SpellCheck(ActionResult.ok(), player, card, definition, target);
    }

    /** actor's player state if it's their turn and they have a deck, else null. */
    private PlayerManager.PlayerState playerToAct(PieceAlignment actor) {
        if (actor == null || actor != currentPlayer()) return null;
        PlayerManager.PlayerState player = GameContext.get().getPlayerManager().get(actor);
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
