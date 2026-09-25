package io.github.elderpath_crusade.abilities.data;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.abilities.stats.StatsModifier;
import io.github.elderpath_crusade.bot.BotManager;
import io.github.elderpath_crusade.data.AbilityRegistry;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.ecs.components.AbilityInstanceComponent;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.IdentityComponent;
import io.github.elderpath_crusade.ecs.components.ModifierComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.ecs.components.StunComponent;
import io.github.elderpath_crusade.ecs.components.TerrainComponent;
import io.github.elderpath_crusade.ecs.factory.PieceFactory;
import io.github.elderpath_crusade.ecs.systems.CombatSystem;
import io.github.elderpath_crusade.ecs.systems.MovementSystem;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.PieceMovedEvent;
import io.github.elderpath_crusade.events.PieceSpawnedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.ClickableTargetType;
import io.github.elderpath_crusade.game.PlayerManager;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.game_objects.cards.Card;
import io.github.elderpath_crusade.game_objects.cards.UnitCard;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.TargetFilter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EffectExecutor {

    private static final ComponentMapper<StatsComponent> statsMapper = ComponentMapper.getFor(StatsComponent.class);
    private static final ComponentMapper<PositionComponent> posMapper = ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<IdentityComponent> idMapper = ComponentMapper.getFor(IdentityComponent.class);
    private static final ComponentMapper<AlignmentComponent> alignMapper = ComponentMapper.getFor(AlignmentComponent.class);

    /** Hard ceiling on Recast iterations regardless of authored maxChains — guards against a data mistake hanging the game. */
    private static final int RECAST_HARD_CEILING = 50;
    private static int timedModifierCounter = 0;

    public static void execute(EffectNode effect, List<Entity> targets, Entity owner, ExpressionContext context, Map<String, Object> abilityState) {
        switch (effect.type()) {
            case "Damage" -> executeDamage(effect, targets, context);
            case "Heal" -> executeHeal(effect, targets, context);
            case "Move" -> executeMove(effect, targets, owner, context);
            case "Swap" -> executeSwap(effect, targets, owner);
            case "ApplyStatus" -> executeApplyStatus(effect, targets, context);
            case "SpendAction" -> executeSpendAction(effect, owner, context);
            case "ModifyState" -> executeModifyState(effect, context, abilityState);
            case "Branch" -> executeBranch(effect, targets, owner, context, abilityState);
            case "Sequence" -> executeSequence(effect, targets, owner, context, abilityState);
            case "ForEach" -> executeForEach(effect, owner, context, abilityState);
            case "AddModifier" -> executeAddModifier(effect, targets);
            case "SetActions" -> executeSetActions(effect, targets, context);
            case "GrantAction" -> executeGrantAction(effect, targets, context);
            case "DrawCard" -> executeDrawCard(owner, context);
            case "DiscardCard" -> executeDiscardCard(effect, context);
            case "SummonPiece" -> executeSummonPiece(effect, owner, context);
            case "GenerateMana" -> executeGenerateMana(effect, owner, context);
            case "AttachTimedModifier" -> executeAttachTimedModifier(effect, targets, context);
            case "RemoveSelfAbility" -> executeRemoveSelfAbility(owner, context);
            case "Recast" -> executeRecast(effect, owner, context, abilityState);
            case "PushAndAdvance" -> executePushAndAdvance(targets, owner);
            case "ChooseTarget" -> executeChooseTarget(effect, owner, context, abilityState);
            default -> {}
        }
    }

    private static void executeDamage(EffectNode effect, List<Entity> targets, ExpressionContext context) {
        int amount = ExpressionEvaluator.evaluateInt(effect.params().get("amount"), context);
        if (amount <= 0) return;
        CombatSystem combat = GameContext.get().getEcsEngine().getSystem(CombatSystem.class);
        for (Entity target : targets) {
            combat.applyDamage(target, amount);
            PositionComponent pos = posMapper.get(target);
            context.set("$lastDamage.target", target);
            context.set("$lastDamage.row", pos != null ? pos.row : -1);
            context.set("$lastDamage.col", pos != null ? pos.col : -1);
            context.set("$lastDamage.targetDied", EntityUtils.isDead(target));
        }
    }

    private static void executeHeal(EffectNode effect, List<Entity> targets, ExpressionContext context) {
        int amount = ExpressionEvaluator.evaluateInt(effect.params().get("amount"), context);
        if (amount <= 0) return;
        for (Entity target : targets) {
            StatsComponent stats = statsMapper.get(target);
            if (stats != null) {
                stats.currentHealth = Math.min(stats.maxHealth, stats.currentHealth + amount);
            }
        }
    }

    private static void executeMove(EffectNode effect, List<Entity> targets, Entity owner, ExpressionContext context) {
        MovementSystem movement = GameContext.get().getEcsEngine().getSystem(MovementSystem.class);
        String destination = (String) effect.params().get("destination");

        if ("$chosenTile".equals(destination)) {
            int destRow = ExpressionEvaluator.evaluateInt(context.get("$chosenTile.row"), context);
            int destCol = ExpressionEvaluator.evaluateInt(context.get("$chosenTile.col"), context);
            for (Entity target : targets) {
                movement.executeForcedMove(target, destRow, destCol, "ability", null);
            }
            return;
        }

        int row = ExpressionEvaluator.evaluateInt(effect.params().get("row"), context);
        int col = ExpressionEvaluator.evaluateInt(effect.params().get("col"), context);
        for (Entity target : targets) {
            movement.executeForcedMove(target, row, col, "ability", null);
        }
    }

    /**
     * Charger's PushOnAttack: pushes the defender back one tile in the direction away
     * from the attacker, then the attacker advances into the tile the defender just
     * vacated. If the push destination is blocked by terrain, the defender takes 1
     * damage instead and neither piece moves; if it's blocked by anything else (off
     * the board, or another unit), the whole thing fizzles — no push, no damage, no
     * advance.
     */
    private static void executePushAndAdvance(List<Entity> targets, Entity owner) {
        if (owner == null || targets.isEmpty()) return;
        Entity defender = targets.get(0);
        PositionComponent ownerPos = posMapper.get(owner);
        PositionComponent defenderPos = posMapper.get(defender);
        if (ownerPos == null || defenderPos == null) return;

        int dRow = defenderPos.row - ownerPos.row;
        int dCol = defenderPos.col - ownerPos.col;
        if (dRow != 0) dRow = dRow > 0 ? 1 : -1;
        if (dCol != 0) dCol = dCol > 0 ? 1 : -1;
        int pushRow = defenderPos.row + dRow;
        int pushCol = defenderPos.col + dCol;

        Board board = GameContext.get().getActiveBoard();
        if (board == null) return;

        boolean inBounds = pushRow >= 0 && pushRow < board.getROWS() && pushCol >= 0 && pushCol < board.getCOLS();
        Entity blocker = inBounds ? board.getEntityAtPos(pushRow, pushCol) : null;

        if (blocker != null && blocker.getComponent(TerrainComponent.class) != null) {
            CombatSystem combat = GameContext.get().getEcsEngine().getSystem(CombatSystem.class);
            combat.applyDamage(defender, 1);
            return;
        }
        if (!inBounds || blocker != null) return; // off-board or blocked by another unit — fizzles

        int defenderFromRow = defenderPos.row, defenderFromCol = defenderPos.col;

        MovementSystem movement = GameContext.get().getEcsEngine().getSystem(MovementSystem.class);
        boolean pushed = movement.executeForcedMove(defender, pushRow, pushCol, "ABILITY", "PushOnAttack");
        if (!pushed) return;
        movement.executeForcedMove(owner, defenderFromRow, defenderFromCol, "ABILITY", "PushOnAttack");
    }

    private static void executeSwap(EffectNode effect, List<Entity> targets, Entity owner) {
        if (targets.isEmpty()) return;
        Entity target = targets.get(0);
        if (target == null || target == owner) return;

        PositionComponent ownerPos = posMapper.get(owner);
        PositionComponent targetPos = posMapper.get(target);
        if (ownerPos == null || targetPos == null) return;

        int ownerRow = ownerPos.row, ownerCol = ownerPos.col;
        int targetRow = targetPos.row, targetCol = targetPos.col;

        io.github.elderpath_crusade.game_objects.board.Board board = GameContext.get().getActiveBoard();
        if (board == null) return;

        board.removeEntityAtPos(ownerRow, ownerCol);
        board.removeEntityAtPos(targetRow, targetCol);

        ownerPos.set(targetRow, targetCol);
        targetPos.set(ownerRow, ownerCol);

        String ownerId = idMapper.get(owner) != null ? idMapper.get(owner).id : "";
        String targetId = idMapper.get(target) != null ? idMapper.get(target).id : "";
        board.addEntityToPos(targetRow, targetCol, owner, ownerId);
        board.addEntityToPos(ownerRow, ownerCol, target, targetId);

        PieceAlignment ownerAlign = alignMapper.get(owner) != null ? alignMapper.get(owner).alignment : PieceAlignment.NEUTRAL;
        PieceAlignment targetAlign = alignMapper.get(target) != null ? alignMapper.get(target).alignment : PieceAlignment.NEUTRAL;
        TypedEventBus.get().emit(new PieceMovedEvent(
                ownerId, ownerAlign, ownerRow, ownerCol, targetRow, targetCol,
                PieceMovedEvent.MovementType.FORCED, "ABILITY", "SwapOnAttack"));
        TypedEventBus.get().emit(new PieceMovedEvent(
                targetId, targetAlign, targetRow, targetCol, ownerRow, ownerCol,
                PieceMovedEvent.MovementType.FORCED, "ABILITY", "SwapOnAttack"));
    }

    private static void executeApplyStatus(EffectNode effect, List<Entity> targets, ExpressionContext context) {
        String status = (String) effect.params().get("status");
        if (status == null) return;
        int turns = ExpressionEvaluator.evaluateInt(effect.params().get("turns"), context);

        for (Entity target : targets) {
            if ("Stun".equals(status)) {
                StunComponent stun = target.getComponent(StunComponent.class);
                if (stun == null) {
                    stun = new StunComponent();
                    target.add(stun);
                }
                stun.turnsRemaining = Math.max(stun.turnsRemaining, turns);
            }
        }
    }

    private static void executeSpendAction(EffectNode effect, Entity owner, ExpressionContext context) {
        Object amountObj = effect.params().get("amount");
        int amount = amountObj != null ? ExpressionEvaluator.evaluateInt(amountObj, context) : 1;
        StatsComponent stats = statsMapper.get(owner);
        if (stats != null) {
            for (int i = 0; i < amount; i++) stats.spendAction();
        }
    }

    private static void executeModifyState(EffectNode effect, ExpressionContext context, Map<String, Object> abilityState) {
        String key = (String) effect.params().get("key");
        String operation = (String) effect.params().get("operation");
        int value = ExpressionEvaluator.evaluateInt(effect.params().get("value"), context);
        if (key == null || operation == null) return;

        Object stored = abilityState.get(key);
        int current = stored == null ? 0
                : stored instanceof Boolean b ? (b ? 1 : 0)
                : ((Number) stored).intValue();
        int newValue = switch (operation) {
            case "Set" -> value;
            case "Add" -> current + value;
            case "Subtract" -> current - value;
            default -> current;
        };
        abilityState.put(key, newValue);
        context.set("$state." + key, newValue);
    }

    private static void executeBranch(EffectNode effect, List<Entity> targets, Entity owner, ExpressionContext context, Map<String, Object> abilityState) {
        Object condObj = effect.params().get("condition");
        boolean result = ConditionEvaluator.evaluate(toCondition(condObj), context);

        List<EffectNode> branch = toEffectNodes(result ? effect.params().get("then") : effect.params().get("else"));
        for (EffectNode node : branch) {
            execute(node, targets, owner, context, abilityState);
        }
    }

    private static void executeSequence(EffectNode effect, List<Entity> targets, Entity owner, ExpressionContext context, Map<String, Object> abilityState) {
        List<EffectNode> steps = toEffectNodes(effect.params().get("steps"));
        for (EffectNode step : steps) {
            execute(step, targets, owner, context, abilityState);
        }
    }

    private static void executeForEach(EffectNode effect, Entity owner, ExpressionContext context, Map<String, Object> abilityState) {
        TargetSelector selector = toSelector(effect.params().get("targets"));
        if (selector == null) return;

        List<Entity> resolved = TargetSelectorResolver.resolve(selector, owner, context);
        List<EffectNode> doEffects = toEffectNodes(effect.params().get("do"));

        for (Entity target : resolved) {
            StatsComponent targetStats = statsMapper.get(target);
            if (targetStats != null) {
                context.withTarget(Map.of(
                        "health", targetStats.currentHealth,
                        "maxHealth", targetStats.maxHealth,
                        "damage", targetStats.damage
                ));
            }
            context.set("$target.stunned", EntityUtils.isStunned(target));
            for (EffectNode node : doEffects) {
                execute(node, List.of(target), owner, context, abilityState);
            }
        }
    }

    private static void executeSetActions(EffectNode effect, List<Entity> targets, ExpressionContext context) {
        int amount = ExpressionEvaluator.evaluateInt(effect.params().get("amount"), context);
        for (Entity target : targets) {
            StatsComponent stats = statsMapper.get(target);
            if (stats != null) {
                stats.remainingActions = amount;
            }
        }
    }

    private static void executeGrantAction(EffectNode effect, List<Entity> targets, ExpressionContext context) {
        int amount = ExpressionEvaluator.evaluateInt(effect.params().get("amount"), context);
        for (Entity target : targets) {
            StatsComponent stats = statsMapper.get(target);
            if (stats != null) {
                stats.remainingActions += amount;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void executeAddModifier(EffectNode effect, List<Entity> targets) {
        Map<String, Object> params = effect.params();
        // Stats may be nested under a "stats" key (from YAML: {target: ..., stats: {addDamage: 1}})
        Object statsObj = params.get("stats");
        Map<String, Object> stats = (statsObj instanceof Map<?, ?> m) ? (Map<String, Object>) m : params;
        applyStatsModifierToTargets(stats, targets);
    }

    /** Adds a permanent (until the entity's own lifetime ends) StatsModifier built from a stats map. */
    private static void applyStatsModifierToTargets(Map<String, Object> stats, List<Entity> targets) {
        StatsModifier mod = new StatsModifier();
        if (stats.containsKey("addDamage")) mod.addDamage = ((Number) stats.get("addDamage")).intValue();
        if (stats.containsKey("addSpeed")) mod.addSpeed = ((Number) stats.get("addSpeed")).intValue();
        if (stats.containsKey("addActions")) mod.addActions = ((Number) stats.get("addActions")).intValue();
        if (stats.containsKey("addMaxHealth")) mod.addMaxHealth = ((Number) stats.get("addMaxHealth")).intValue();
        if (stats.containsKey("addRange")) mod.addRange = ((Number) stats.get("addRange")).intValue();

        for (Entity target : targets) {
            ModifierComponent mc = target.getComponent(ModifierComponent.class);
            if (mc != null) {
                mc.accumulator.add(mod);
            }
        }
    }

    private static void executeDrawCard(Entity owner, ExpressionContext context) {
        PieceAlignment alignment = resolveCasterAlignment(owner, context);
        if (alignment == null) return;
        PlayerManager.PlayerState playerState = GameContext.get().getPlayerManager().get(alignment);
        if (playerState == null || playerState.deck == null || playerState.hand == null) return;

        playerState.deck.draw();
        List<Card> cards = playerState.hand.getCards();
        if (cards.isEmpty()) return;
        Card drawn = cards.get(cards.size() - 1);

        context.set("$drawn.card", drawn);
        if (drawn instanceof UnitCard unitCard) {
            context.set("$drawn.isPiece", true);
            context.set("$drawn.cost", unitCard.getStatsCost());
            context.set("$drawn.registryKey", unitCard.getRegistryKey());
        } else {
            context.set("$drawn.isPiece", false);
            context.set("$drawn.cost", 0);
        }
    }

    private static void executeDiscardCard(EffectNode effect, ExpressionContext context) {
        Object ref = effect.params().get("target");
        Card card = resolveCardReference(ref, context);
        if (card != null) card.consume();
    }

    /**
     * Resolves the piece to summon and, if the caster's zone has at least one empty
     * tile, lets the player pick which one — fizzling silently if none is available.
     * Consumes the source card (if summoning "$drawn") only once a tile is actually
     * picked, not merely because a valid tile existed.
     */
    private static void executeSummonPiece(EffectNode effect, Entity owner, ExpressionContext context) {
        PieceAlignment alignment = resolveCasterAlignment(owner, context);
        if (alignment == null) return;

        String registryKey;
        Card sourceCard = null;
        Object pieceParam = effect.params().get("piece");
        Object sourceParam = effect.params().get("source");
        if (pieceParam instanceof String pieceName) {
            registryKey = PieceRegistry.toRegistryKey(pieceName);
        } else if ("$drawn".equals(sourceParam)) {
            Object regKey = context.get("$drawn.registryKey");
            if (!(regKey instanceof String)) return;
            registryKey = (String) regKey;
            sourceCard = resolveCardReference("$drawn", context);
        } else {
            return;
        }

        PieceDefinition def = PieceRegistry.get(registryKey);
        if (def == null) return;

        Board board = GameContext.get().getActiveBoard();
        if (board == null) return;
        List<Plot> slots = findEmptySummonSlots(board, alignment);
        if (slots.isEmpty()) return; // no empty tile in caster's zone — summon silently fizzles

        Card cardToConsume = sourceCard;
        TargetFilter filter = new TargetFilter() {
            @Override
            public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
                return box instanceof Plot plot && slots.contains(plot);
            }

            @Override
            public List<Plot> getEligibleTargets(int targetIndex) {
                return slots;
            }
        };

        GameContext.get().getInteractionManager().requestPick(
                ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1),
                filter,
                (picks) -> {
                    CustomBox chosen = picks.get(1);
                    if (!(chosen instanceof Plot plot)) return;
                    int[] idx = plot.getIndices();

                    Entity piece = PieceFactory.createPiece(def, 0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(),
                            alignment, idx[0], idx[1]);
                    String pieceId = EntityUtils.getId(piece);
                    board.addEntityToPos(idx[0], idx[1], piece, pieceId);
                    TypedEventBus.get().emit(new PieceSpawnedEvent(pieceId, alignment, idx[0], idx[1]));

                    if (cardToConsume != null) cardToConsume.consume();
                }
        );
    }

    private static void executeGenerateMana(EffectNode effect, Entity owner, ExpressionContext context) {
        int amount = ExpressionEvaluator.evaluateInt(effect.params().get("amount"), context);
        if (amount <= 0) return;
        PieceAlignment alignment = resolveCasterAlignment(owner, context);
        if (alignment == null) return;
        PlayerManager.PlayerState playerState = GameContext.get().getPlayerManager().get(alignment);
        if (playerState != null) playerState.addMana(amount);
    }

    /**
     * Attaches a stat modifier to each target. With no "turns" param (or turns <= 0) this
     * is permanent for the entity's lifetime — "until end of battle", since entities never
     * outlive the current match — identical to AddModifier. With turns > 0, builds a small
     * self-contained AbilityDefinition (unique id, Self-targeted modifier, an ON_TURN_END
     * reaction that counts down and removes itself once it reaches 0) and attaches it to
     * the target's AbilityInstanceComponent — the existing passive-modifier machinery
     * (PassiveModifierSystem) then applies/cleans up the stat change automatically for as
     * long as that ability stays attached.
     */
    @SuppressWarnings("unchecked")
    private static void executeAttachTimedModifier(EffectNode effect, List<Entity> targets, ExpressionContext context) {
        Object statsObj = effect.params().get("stats");
        Map<String, Object> stats = (statsObj instanceof Map<?, ?> m) ? (Map<String, Object>) m : Map.of();
        int turns = effect.params().containsKey("turns")
                ? ExpressionEvaluator.evaluateInt(effect.params().get("turns"), context) : 0;

        if (turns <= 0) {
            applyStatsModifierToTargets(stats, targets);
            return;
        }

        for (Entity target : targets) {
            AbilityInstanceComponent aic = target.getComponent(AbilityInstanceComponent.class);
            if (aic == null) {
                aic = new AbilityInstanceComponent();
                target.add(aic);
            }
            String id = "TimedMod#" + (++timedModifierCounter);
            AbilityDefinition timed = new AbilityDefinition(
                    id, "", Map.of("turnsRemaining", turns),
                    List.of(new Reaction(TriggerType.ON_TURN_END, null, List.of(
                            new EffectNode("ModifyState", Map.of(
                                    "key", "turnsRemaining", "operation", "Subtract", "value", 1)),
                            new EffectNode("Branch", Map.of(
                                    "condition", new Condition("Compare", Map.of(
                                            "left", "$state.turnsRemaining", "op", "<=", "right", 0)),
                                    "then", List.of(new EffectNode("RemoveSelfAbility", Map.of()))))
                    ))),
                    null,
                    List.of(new ModifierDef(new TargetSelector("Self"), stats)));
            aic.addAbility(timed);
        }
    }

    /** Removes the ability currently executing (identified by $ability.id) from its owner. */
    private static void executeRemoveSelfAbility(Entity owner, ExpressionContext context) {
        if (owner == null) return;
        Object idObj = context.get("$ability.id");
        if (!(idObj instanceof String id)) return;
        AbilityInstanceComponent aic = owner.getComponent(AbilityInstanceComponent.class);
        if (aic != null) aic.removeAbility(id);
    }

    /**
     * While "condition" holds and "newTargets" resolves to a match, runs "effects" against
     * the newly resolved target and re-checks — bounded by "maxChains" (author-tunable,
     * default 10) and a fixed hard ceiling so a data mistake can never hang the game.
     */
    private static void executeRecast(EffectNode effect, Entity owner, ExpressionContext context, Map<String, Object> abilityState) {
        Object condObj = effect.params().get("condition");
        int maxChains = effect.params().containsKey("maxChains")
                ? ExpressionEvaluator.evaluateInt(effect.params().get("maxChains"), context) : 10;
        int limit = Math.min(maxChains, RECAST_HARD_CEILING);

        TargetSelector selector = toSelector(effect.params().get("newTargets"));
        List<EffectNode> body = toEffectNodes(effect.params().get("effects"));
        if (selector == null || body.isEmpty()) return;

        int chains = 0;
        while (chains < limit && ConditionEvaluator.evaluate(toCondition(condObj), context)) {
            List<Entity> next = TargetSelectorResolver.resolve(selector, owner, context);
            if (next.isEmpty()) break;
            Entity target = next.get(0);
            context.set("$chosen", target);
            for (EffectNode node : body) {
                execute(node, List.of(target), owner, context, abilityState);
            }
            chains++;
        }
    }

    private static PieceAlignment resolveCasterAlignment(Entity owner, ExpressionContext context) {
        Object raw = context.get("$caster.alignment");
        if (raw == null && owner != null) {
            AlignmentComponent align = alignMapper.get(owner);
            if (align != null) raw = align.alignment.name();
        }
        if (raw == null) return null;
        try {
            return PieceAlignment.valueOf(raw.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Card resolveCardReference(Object ref, ExpressionContext context) {
        if ("$drawn".equals(ref)) {
            Object card = context.get("$drawn.card");
            return card instanceof Card c ? c : null;
        }
        return null;
    }

    /** Every empty tile in the caster's own summon zone (mirrors Board.isValidSummonTarget, used by SummonCard). */
    private static List<Plot> findEmptySummonSlots(Board board, PieceAlignment alignment) {
        List<Plot> slots = new ArrayList<>();
        for (int row = 0; row < board.getROWS(); row++) {
            for (int col = 0; col < board.getCOLS(); col++) {
                Object cell = board.getPlotAtPos(row, col);
                if (cell instanceof Plot plot && board.isValidSummonTarget(plot, alignment)) {
                    slots.add(plot);
                }
            }
        }
        return slots;
    }

    /**
     * Lets the owner pick which of the resolved "candidates" to run "effects" against,
     * rather than the engine auto-picking one — e.g. RogueFreeStrike choosing which
     * adjacent enemy to strike. Fizzles silently if there are no candidates.
     */
    private static void executeChooseTarget(EffectNode effect, Entity owner, ExpressionContext context, Map<String, Object> abilityState) {
        if (owner == null) return;
        TargetSelector selector = toSelector(effect.params().get("candidates"));
        if (selector == null) return;
        List<Entity> candidates = TargetSelectorResolver.resolve(selector, owner, context);
        if (candidates.isEmpty()) return;

        List<EffectNode> body = toEffectNodes(effect.params().get("effects"));
        if (body.isEmpty()) return;

        Board board = GameContext.get().getActiveBoard();
        if (board == null) return;

        Map<Plot, Entity> byPlot = new HashMap<>();
        for (Entity candidate : candidates) {
            PositionComponent pos = posMapper.get(candidate);
            if (pos == null) continue;
            Object cell = board.getPlotAtPos(pos.row, pos.col);
            if (cell instanceof Plot plot) byPlot.put(plot, candidate);
        }
        if (byPlot.isEmpty()) return;
        List<Plot> plots = new ArrayList<>(byPlot.keySet());

        if (BotManager.isBotControlled(EntityUtils.getAlignment(owner))) {
            Entity chosen = pickBestCandidate(candidates);
            if (chosen == null) return;
            context.set("$chosen", chosen);
            for (EffectNode node : body) {
                execute(node, List.of(chosen), owner, context, abilityState);
            }
            return;
        }

        TargetFilter filter = new TargetFilter() {
            @Override
            public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
                return box instanceof Plot plot && plots.contains(plot);
            }

            @Override
            public List<Plot> getEligibleTargets(int targetIndex) {
                return plots;
            }
        };

        GameContext.get().getInteractionManager().requestPick(
                ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1),
                filter,
                (picks) -> {
                    CustomBox chosenBox = picks.get(1);
                    if (!(chosenBox instanceof Plot plot)) return;
                    Entity chosen = byPlot.get(plot);
                    if (chosen == null) return;
                    context.set("$chosen", chosen);
                    for (EffectNode node : body) {
                        execute(node, List.of(chosen), owner, context, abilityState);
                    }
                }
        );
    }

    /** Bot's target pick for ChooseTarget: favor the candidate closest to dying, so the strike is most likely to secure a kill. */
    private static Entity pickBestCandidate(List<Entity> candidates) {
        Entity best = null;
        int bestHealth = Integer.MAX_VALUE;
        for (Entity candidate : candidates) {
            int health = EntityUtils.getCurrentHealth(candidate);
            if (best == null || health < bestHealth) {
                best = candidate;
                bestHealth = health;
            }
        }
        return best;
    }

    @SuppressWarnings("unchecked")
    private static TargetSelector toSelector(Object selectorObj) {
        if (selectorObj instanceof TargetSelector ts) return ts;
        if (selectorObj instanceof String s) return new TargetSelector(s);
        if (selectorObj instanceof Map<?, ?> map) {
            String type = (String) map.get("type");
            Map<String, Object> params = (Map<String, Object>) map.get("params");
            return new TargetSelector(type, params);
        }
        return null;
    }

    /**
     * Converts a raw inline "condition" value (Branch/Recast) into a Condition record.
     * Unlike top-level effects, an inline condition map is NOT run through
     * AbilityDataParsing.parseConditions at load time, so it keeps its flat shape
     * (extra keys directly on the map, not nested under "params") — matching how
     * reaction-level conditions look once parsed.
     */
    @SuppressWarnings("unchecked")
    private static Condition toCondition(Object condObj) {
        if (condObj instanceof Condition c) return c;
        if (condObj instanceof Map<?, ?> map) {
            return new Condition((String) map.get("type"), (Map<String, Object>) map);
        }
        return new Condition("Always", Map.of());
    }

    @SuppressWarnings("unchecked")
    private static List<EffectNode> toEffectNodes(Object obj) {
        if (!(obj instanceof List<?> list)) return List.of();
        List<EffectNode> nodes = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof EffectNode en) nodes.add(en);
            else if (item instanceof Map<?, ?> map) nodes.add(toEffectNode(map));
        }
        return nodes;
    }

    /**
     * Converts a raw YAML-shaped effect map into an EffectNode, flattening a nested
     * "params" key into the top level — the same convention AbilityDataParsing.parseEffects
     * applies to top-level effects, needed here too since nested effects (Branch then/else,
     * ForEach do, Sequence steps, Recast effects) are only converted at execution time.
     */
    @SuppressWarnings("unchecked")
    private static EffectNode toEffectNode(Map<?, ?> map) {
        String type = (String) map.get("type");
        Map<String, Object> params = new java.util.HashMap<>((Map<String, Object>) map);
        params.remove("type");
        if (params.containsKey("params")) {
            params.putAll((Map<String, Object>) params.remove("params"));
        }
        return new EffectNode(type, params);
    }
}
