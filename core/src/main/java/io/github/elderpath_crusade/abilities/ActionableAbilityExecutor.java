package io.github.elderpath_crusade.abilities;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.abilities.data.AbilityDefinition;
import io.github.elderpath_crusade.abilities.data.ActionDef;
import io.github.elderpath_crusade.abilities.data.Cost;
import io.github.elderpath_crusade.abilities.data.EffectExecutor;
import io.github.elderpath_crusade.abilities.data.EffectNode;
import io.github.elderpath_crusade.abilities.data.ExpressionContext;
import io.github.elderpath_crusade.abilities.data.TargetSelector;
import io.github.elderpath_crusade.abilities.data.TargetSelectorResolver;
import io.github.elderpath_crusade.ecs.components.AbilityInstanceComponent;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.IdentityComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.events.ActionSpentEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game.PlayerManager;
import io.github.elderpath_crusade.match.BoardGrid;
import io.github.elderpath_crusade.match.ActionResult;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.PickKind;
import io.github.elderpath_crusade.match.PickRequest;
import io.github.elderpath_crusade.match.PickRouter;
import io.github.elderpath_crusade.enums.PieceAlignment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ActionableAbilityExecutor {

    private static final ComponentMapper<StatsComponent> statsMapper = ComponentMapper.getFor(StatsComponent.class);
    private static final ComponentMapper<PositionComponent> posMapper = ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<AlignmentComponent> alignMapper = ComponentMapper.getFor(AlignmentComponent.class);
    private static final ComponentMapper<IdentityComponent> idMapper = ComponentMapper.getFor(IdentityComponent.class);
    private static final ComponentMapper<AbilityInstanceComponent> aicMapper = ComponentMapper.getFor(AbilityInstanceComponent.class);

    /**
     * Runs actionDef of owner's ability. Callers validate first (AbilityRules — GameServer's
     * activateAbility); this re-checks affordability as a safety net. A targeted action asks
     * its owner through the PickRouter and resolves in the answer; a cancelled pick runs no
     * effect and pays no cost. Rejected with NO_VALID_TARGET (nothing started) when the action
     * needs a target and there is none.
     */
    public static ActionResult execute(Entity owner, AbilityDefinition abilityDef, ActionDef actionDef) {
        if (!canAffordCosts(owner, actionDef.costs())) return ActionResult.rejected(ActionResult.Reason.NOT_ENOUGH_MANA);

        ExpressionContext ctx = buildContext(owner);
        AbilityInstanceComponent aic = aicMapper.get(owner);
        Map<String, Object> abilityState = (aic != null)
                ? aic.state.getOrDefault(abilityDef.id(), new HashMap<>())
                : new HashMap<>();

        TargetSelector selector = actionDef.targetSelector();
        String selectorType = (selector != null) ? selector.type() : "Self";

        return switch (selectorType) {
            case "Self" -> {
                executeEffects(actionDef.effects(), owner, ctx, abilityState);
                deductCosts(owner, actionDef.costs());
                yield ActionResult.ok();
            }
            case "ChooseEnemy" -> startSinglePick(owner, abilityDef, actionDef, ctx, abilityState, AlignmentFilter.ENEMY);
            case "ChooseFriendly" -> startSinglePick(owner, abilityDef, actionDef, ctx, abilityState, AlignmentFilter.FRIENDLY);
            case "ChooseAny" -> startSinglePick(owner, abilityDef, actionDef, ctx, abilityState, AlignmentFilter.ANY);
            case "ChooseRow" -> startRowPick(owner, abilityDef, actionDef, ctx, abilityState);
            case "ChooseTile" -> startTilePickAnywhere(owner, abilityDef, actionDef, ctx, abilityState);
            default -> ActionResult.rejected(ActionResult.Reason.EXECUTION_FAILED);
        };
    }

    /**
     * Checks whether the owner can pay every cost of an action (Action points and/or Mana)
     * without deducting anything. Callers must not start a targeting flow or execute effects
     * if this returns false.
     */
    static boolean canAffordCosts(Entity owner, List<Cost> costs) {
        if (costs == null) return true;
        StatsComponent stats = statsMapper.get(owner);
        AlignmentComponent align = alignMapper.get(owner);
        for (Cost cost : costs) {
            if ("Action".equals(cost.type())) {
                if (stats == null || stats.remainingActions < cost.amount()) return false;
            } else if ("Mana".equals(cost.type())) {
                if (align == null) return false;
                PlayerManager.PlayerState playerState = GameContext.get().getPlayerManager().get(align.alignment);
                if (playerState == null || playerState.getMana() < cost.amount()) return false;
            }
        }
        return true;
    }

    private static ExpressionContext buildContext(Entity owner) {
        ExpressionContext ctx = new ExpressionContext();
        StatsComponent stats = statsMapper.get(owner);
        if (stats != null) {
            ctx.set("$self.health", stats.currentHealth);
            ctx.set("$self.maxHealth", stats.maxHealth);
            ctx.set("$self.damage", stats.damage);
            ctx.set("$self.speed", stats.speed);
            ctx.set("$self.actions", stats.actions);
            ctx.set("$self.remainingActions", stats.remainingActions);
        }
        PositionComponent pos = posMapper.get(owner);
        if (pos != null) {
            ctx.set("$self.row", pos.row);
            ctx.set("$self.col", pos.col);
        }
        AlignmentComponent align = alignMapper.get(owner);
        if (align != null) ctx.set("$self.alignment", align.alignment.name());
        return ctx;
    }

    static void deductCosts(Entity owner, List<Cost> costs) {
        StatsComponent stats = statsMapper.get(owner);
        IdentityComponent id = idMapper.get(owner);
        AlignmentComponent align = alignMapper.get(owner);
        if (stats == null) return;
        for (Cost cost : costs) {
            if ("Action".equals(cost.type())) {
                for (int i = 0; i < cost.amount(); i++) {
                    stats.spendAction();
                }
                if (id != null && align != null) {
                    TypedEventBus.get().emit(new ActionSpentEvent(id.id, align.alignment, stats.remainingActions));
                }
            } else if ("Mana".equals(cost.type()) && align != null) {
                PlayerManager.PlayerState playerState = GameContext.get().getPlayerManager().get(align.alignment);
                if (playerState != null) {
                    playerState.addMana(-cost.amount());
                }
            }
        }
    }

    private static void executeEffects(List<EffectNode> effects, Entity owner, ExpressionContext ctx, Map<String, Object> abilityState) {
        for (EffectNode effect : effects) {
            List<Entity> targets = resolveTarget(effect, owner, ctx);
            EffectExecutor.execute(effect, targets, owner, ctx, abilityState);
        }
    }

    private static List<Entity> resolveTarget(EffectNode effect, Entity owner, ExpressionContext context) {
        Object targetParam = effect.params().get("target");
        if (targetParam instanceof String s) {
            if ("$self".equals(s)) return List.of(owner);
            if ("$chosen".equals(s)) {
                Object ref = context.get("$chosen");
                return (ref instanceof Entity e) ? List.of(e) : List.of();
            }
            return TargetSelectorResolver.resolve(new TargetSelector(s), owner, context);
        }
        return List.of();
    }

    private enum AlignmentFilter { ENEMY, FRIENDLY, ANY }

    private static boolean matchesAlignment(AlignmentFilter filter, boolean isEnemy) {
        return switch (filter) {
            case ENEMY -> isEnemy;
            case FRIENDLY -> !isEnemy;
            case ANY -> true;
        };
    }

    private static ActionResult startSinglePick(Entity owner, AbilityDefinition abilityDef, ActionDef actionDef,
            ExpressionContext ctx, Map<String, Object> abilityState, AlignmentFilter alignmentFilter) {
        TargetSelector selector = actionDef.targetSelector();
        int range = selector.params() != null && selector.params().containsKey("range")
                ? ((Number) selector.params().get("range")).intValue() : 99;

        PositionComponent ownerPos = posMapper.get(owner);
        AlignmentComponent ownerAlign = alignMapper.get(owner);
        if (ownerPos == null || ownerAlign == null) return ActionResult.rejected(ActionResult.Reason.EXECUTION_FAILED);

        List<Coord> candidates = singlePickCandidates(owner, ownerPos, ownerAlign, range, alignmentFilter);
        if (candidates.isEmpty()) return ActionResult.rejected(ActionResult.Reason.NO_VALID_TARGET);

        boolean needsTilePick = actionDef.effects().stream().anyMatch(e -> "$chosenTile".equals(e.params().get("destination")));

        pick(ownerAlign.alignment, PickKind.PIECE, candidates, abilityDef, choice -> {
            BoardGrid grid = GameContext.get().getActiveGrid();
            Entity chosenEntity = grid == null ? null : grid.getEntityAt(choice.row(), choice.col());
            if (chosenEntity == null) return; // the piece left since the question was asked — fizzle
            ctx.set("$chosen", chosenEntity);
            ctx.set("$chosen.row", choice.row());
            ctx.set("$chosen.col", choice.col());
            if (needsTilePick) {
                startTilePick(owner, abilityDef, actionDef, ctx, abilityState, chosenEntity);
            } else {
                executeEffects(actionDef.effects(), owner, ctx, abilityState);
                deductCosts(owner, actionDef.costs());
            }
        });
        return ActionResult.ok();
    }

    private static void startTilePick(Entity owner, AbilityDefinition abilityDef, ActionDef actionDef, ExpressionContext ctx,
            Map<String, Object> abilityState, Entity chosenEntity) {
        PositionComponent chosenPos = posMapper.get(chosenEntity);
        AlignmentComponent ownerAlign = alignMapper.get(owner);
        if (chosenPos == null || ownerAlign == null) return;

        // A follow-up question: no empty tile next to the chosen piece → the action fizzles, unpaid.
        pick(ownerAlign.alignment, PickKind.TILE, tilesInRange(chosenPos.row, chosenPos.col, 1, true), abilityDef, tile -> {
            ctx.set("$chosenTile.row", tile.row());
            ctx.set("$chosenTile.col", tile.col());
            executeEffects(actionDef.effects(), owner, ctx, abilityState);
            deductCosts(owner, actionDef.costs());
        });
    }

    /**
     * Lets the owner pick any tile (occupied or empty) within range of itself, setting
     * $chosen.row / $chosen.col for use by effects. Used by area-effect abilities like
     * StormAction that target a point on the board rather than a specific unit.
     */
    private static ActionResult startTilePickAnywhere(Entity owner, AbilityDefinition abilityDef, ActionDef actionDef,
            ExpressionContext ctx, Map<String, Object> abilityState) {
        TargetSelector selector = actionDef.targetSelector();
        int range = selector.params() != null && selector.params().containsKey("range")
                ? ((Number) selector.params().get("range")).intValue() : 99;

        PositionComponent ownerPos = posMapper.get(owner);
        AlignmentComponent ownerAlign = alignMapper.get(owner);
        if (ownerPos == null || ownerAlign == null) return ActionResult.rejected(ActionResult.Reason.EXECUTION_FAILED);

        List<Coord> candidates = tilesInRange(ownerPos.row, ownerPos.col, range, false);
        if (candidates.isEmpty()) return ActionResult.rejected(ActionResult.Reason.NO_VALID_TARGET);
        pick(ownerAlign.alignment, PickKind.TILE, candidates, abilityDef, choice -> {
            ctx.set("$chosen.row", choice.row());
            ctx.set("$chosen.col", choice.col());
            executeEffects(actionDef.effects(), owner, ctx, abilityState);
            deductCosts(owner, actionDef.costs());
        });
        return ActionResult.ok();
    }

    private static ActionResult startRowPick(Entity owner, AbilityDefinition abilityDef, ActionDef actionDef,
            ExpressionContext ctx, Map<String, Object> abilityState) {
        AlignmentComponent ownerAlign = alignMapper.get(owner);
        BoardGrid grid = GameContext.get().getActiveGrid();
        if (ownerAlign == null || grid == null) return ActionResult.rejected(ActionResult.Reason.EXECUTION_FAILED);

        // Any row may be chosen: every tile is a candidate, and the chosen tile's row counts.
        List<Coord> candidates = tilesInRange(0, 0, Math.max(grid.getRows(), grid.getCols()), false);
        pick(ownerAlign.alignment, PickKind.ROW, candidates, abilityDef, choice -> {
            ctx.set("$chosen", choice.row());
            executeEffects(actionDef.effects(), owner, ctx, abilityState);
            deductCosts(owner, actionDef.costs());
        });
        return ActionResult.ok();
    }

    /** Asks owner to pick among candidates; onChosen runs with the validated choice (never on cancel). */
    private static void pick(PieceAlignment owner, PickKind kind, List<Coord> candidates, AbilityDefinition abilityDef,
            Consumer<Coord> onChosen) {
        PickRouter router = GameContext.get().getPickRouter();
        PickRequest request = router.newRequest(owner, kind, candidates, abilityDef.id(), true);
        router.requestPick(request, response -> {
            if (!response.isCancelled()) onChosen.accept(response.choice());
        });
    }

    // --- Pick candidate logic (pure coordinates) ---

    private static boolean isSinglePickCandidate(Entity entity, Entity owner, PositionComponent ownerPos,
            AlignmentComponent ownerAlign, int range, AlignmentFilter alignmentFilter) {
        if (entity == null || entity == owner) return false;
        PositionComponent pos = posMapper.get(entity);
        AlignmentComponent align = alignMapper.get(entity);
        if (pos == null || align == null) return false;
        if (chebyshev(pos.row, pos.col, ownerPos.row, ownerPos.col) > range) return false;
        boolean isEnemy = align.alignment != ownerAlign.alignment;
        return matchesAlignment(alignmentFilter, isEnemy);
    }

    /** Positions of every piece the owner may pick for a ChooseEnemy/ChooseFriendly/ChooseAny action. */
    private static List<Coord> singlePickCandidates(Entity owner, PositionComponent ownerPos,
            AlignmentComponent ownerAlign, int range, AlignmentFilter alignmentFilter) {
        ImmutableArray<Entity> all = GameContext.get().getEcsEngine().getEntitiesFor(
                Family.all(PositionComponent.class, AlignmentComponent.class).get());
        List<Coord> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            Entity entity = all.get(i);
            if (!isSinglePickCandidate(entity, owner, ownerPos, ownerAlign, range, alignmentFilter)) continue;
            PositionComponent pos = posMapper.get(entity);
            out.add(new Coord(pos.row, pos.col));
        }
        return out;
    }

    /** In-bounds tiles within Chebyshev distance range of (row, col), optionally only empty ones. */
    private static List<Coord> tilesInRange(int row, int col, int range, boolean emptyOnly) {
        BoardGrid grid = GameContext.get().getActiveGrid();
        if (grid == null) return List.of();
        List<Coord> out = new ArrayList<>();
        for (int r = 0; r < grid.getRows(); r++) {
            for (int c = 0; c < grid.getCols(); c++) {
                if (chebyshev(r, c, row, col) > range) continue;
                if (emptyOnly && grid.isOccupied(r, c)) continue;
                out.add(new Coord(r, c));
            }
        }
        return out;
    }

    private static int chebyshev(int r1, int c1, int r2, int c2) {
        return Math.max(Math.abs(r1 - r2), Math.abs(c1 - c2));
    }
}
