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
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.ecs.components.AbilityInstanceComponent;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.IdentityComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.enums.ClickableTargetType;
import io.github.elderpath_crusade.events.ActionSpentEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game.PlayerManager;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.TargetFilter;
import io.github.elderpath_crusade.match.BoardGrid;
import io.github.elderpath_crusade.match.Coord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActionableAbilityExecutor {

    private static final ComponentMapper<StatsComponent> statsMapper = ComponentMapper.getFor(StatsComponent.class);
    private static final ComponentMapper<PositionComponent> posMapper = ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<AlignmentComponent> alignMapper = ComponentMapper.getFor(AlignmentComponent.class);
    private static final ComponentMapper<IdentityComponent> idMapper = ComponentMapper.getFor(IdentityComponent.class);
    private static final ComponentMapper<AbilityInstanceComponent> aicMapper = ComponentMapper.getFor(AbilityInstanceComponent.class);

    public static void execute(Entity owner, AbilityDefinition abilityDef, ActionDef actionDef) {
        if (!canAffordCosts(owner, actionDef.costs())) return;

        ExpressionContext ctx = buildContext(owner);
        AbilityInstanceComponent aic = aicMapper.get(owner);
        Map<String, Object> abilityState = (aic != null)
                ? aic.state.getOrDefault(abilityDef.id(), new HashMap<>())
                : new HashMap<>();

        TargetSelector selector = actionDef.targetSelector();
        String selectorType = (selector != null) ? selector.type() : "Self";

        switch (selectorType) {
            case "Self" -> {
                executeEffects(actionDef.effects(), owner, ctx, abilityState);
                deductCosts(owner, actionDef.costs());
            }
            case "ChooseEnemy" -> startSinglePick(owner, abilityDef, actionDef, ctx, abilityState, AlignmentFilter.ENEMY);
            case "ChooseFriendly" -> startSinglePick(owner, abilityDef, actionDef, ctx, abilityState, AlignmentFilter.FRIENDLY);
            case "ChooseAny" -> startSinglePick(owner, abilityDef, actionDef, ctx, abilityState, AlignmentFilter.ANY);
            case "ChooseRow" -> startRowPick(owner, abilityDef, actionDef, ctx, abilityState);
            case "ChooseTile" -> startTilePickAnywhere(owner, actionDef, ctx, abilityState);
        }
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

    private static void startSinglePick(Entity owner, AbilityDefinition abilityDef, ActionDef actionDef,
            ExpressionContext ctx, Map<String, Object> abilityState, AlignmentFilter alignmentFilter) {
        TargetSelector selector = actionDef.targetSelector();
        int range = selector.params() != null && selector.params().containsKey("range")
                ? ((Number) selector.params().get("range")).intValue() : 99;

        PositionComponent ownerPos = posMapper.get(owner);
        AlignmentComponent ownerAlign = alignMapper.get(owner);
        if (ownerPos == null || ownerAlign == null) return;

        TargetFilter filter = new TargetFilter() {
            @Override
            public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
                if (!(box instanceof Plot plot)) return false;
                BoardGrid grid = GameContext.get().getActiveGrid();
                if (grid == null) return false;
                Entity entity = grid.getEntityAt(plot.getRow(), plot.getCol());
                return isSinglePickCandidate(entity, owner, ownerPos, ownerAlign, range, alignmentFilter);
            }

            @Override
            public List<Plot> getEligibleTargets(int targetIndex) {
                return toPlots(singlePickCandidates(owner, ownerPos, ownerAlign, range, alignmentFilter));
            }
        };

        boolean needsTilePick = actionDef.effects().stream().anyMatch(e -> "$chosenTile".equals(e.params().get("destination")));

        GameContext.get().getInteractionManager().requestPick(
                ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1),
                filter,
                (picks) -> {
                    CustomBox chosen = picks.get(1);
                    if (!(chosen instanceof Plot plot)) return;
                    Entity chosenEntity = GameContext.get().getActiveBoard().getEntityAtPlot(plot);
                    if (chosenEntity != null) {
                        int[] idx = plot.getIndices();
                        ctx.set("$chosen", chosenEntity);
                        ctx.set("$chosen.row", idx[0]);
                        ctx.set("$chosen.col", idx[1]);
                    }
                    if (needsTilePick && chosenEntity != null) {
                        startTilePick(owner, actionDef, ctx, abilityState, chosenEntity);
                    } else {
                        executeEffects(actionDef.effects(), owner, ctx, abilityState);
                        deductCosts(owner, actionDef.costs());
                    }
                }
        );
    }

    private static void startTilePick(Entity owner, ActionDef actionDef, ExpressionContext ctx,
            Map<String, Object> abilityState, Entity chosenEntity) {
        PositionComponent chosenPos = posMapper.get(chosenEntity);
        if (chosenPos == null) return;

        TargetFilter filter = new TargetFilter() {
            @Override
            public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
                if (!(box instanceof Plot plot)) return false;
                return tilesInRange(chosenPos.row, chosenPos.col, 1, true).contains(new Coord(plot.getRow(), plot.getCol()));
            }

            @Override
            public List<Plot> getEligibleTargets(int targetIndex) {
                return toPlots(tilesInRange(chosenPos.row, chosenPos.col, 1, true));
            }
        };

        GameContext.get().getInteractionManager().requestPick(
                ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1),
                filter,
                (picks) -> {
                    CustomBox tile = picks.get(1);
                    if (!(tile instanceof Plot plot)) return;
                    int[] idx = plot.getIndices();
                    ctx.set("$chosenTile.row", idx[0]);
                    ctx.set("$chosenTile.col", idx[1]);
                    executeEffects(actionDef.effects(), owner, ctx, abilityState);
                    deductCosts(owner, actionDef.costs());
                }
        );
    }

    /**
     * Lets the owner pick any tile (occupied or empty) within range of itself, setting
     * $chosen.row / $chosen.col for use by effects. Used by area-effect abilities like
     * StormAction that target a point on the board rather than a specific unit.
     */
    private static void startTilePickAnywhere(Entity owner, ActionDef actionDef,
            ExpressionContext ctx, Map<String, Object> abilityState) {
        TargetSelector selector = actionDef.targetSelector();
        int range = selector.params() != null && selector.params().containsKey("range")
                ? ((Number) selector.params().get("range")).intValue() : 99;

        PositionComponent ownerPos = posMapper.get(owner);
        if (ownerPos == null) return;

        TargetFilter filter = new TargetFilter() {
            @Override
            public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
                if (!(box instanceof Plot plot)) return false;
                return chebyshev(plot.getRow(), plot.getCol(), ownerPos.row, ownerPos.col) <= range;
            }

            @Override
            public List<Plot> getEligibleTargets(int targetIndex) {
                return toPlots(tilesInRange(ownerPos.row, ownerPos.col, range, false));
            }
        };

        GameContext.get().getInteractionManager().requestPick(
                ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1),
                filter,
                (picks) -> {
                    CustomBox chosen = picks.get(1);
                    if (!(chosen instanceof Plot plot)) return;
                    int[] idx = plot.getIndices();
                    ctx.set("$chosen.row", idx[0]);
                    ctx.set("$chosen.col", idx[1]);
                    executeEffects(actionDef.effects(), owner, ctx, abilityState);
                    deductCosts(owner, actionDef.costs());
                }
        );
    }

    private static void startRowPick(Entity owner, AbilityDefinition abilityDef, ActionDef actionDef,
            ExpressionContext ctx, Map<String, Object> abilityState) {
        TargetFilter filter = new TargetFilter() {
            @Override
            public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
                return box instanceof Plot;
            }

            @Override
            public List<Plot> getEligibleTargets(int targetIndex) {
                return null;
            }
        };

        GameContext.get().getInteractionManager().requestPick(
                ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1),
                filter,
                (picks) -> {
                    CustomBox chosen = picks.get(1);
                    if (!(chosen instanceof Plot plot)) return;
                    int[] idx = plot.getIndices();
                    ctx.set("$chosen", idx[0]);
                    executeEffects(actionDef.effects(), owner, ctx, abilityState);
                    deductCosts(owner, actionDef.costs());
                }
        );
    }

    // --- Pick candidate logic (pure coordinates; the TargetFilters above only map to Plots) ---

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

    private static List<Plot> toPlots(List<Coord> coords) {
        Board board = GameContext.get().getActiveBoard();
        if (board == null) return List.of();
        List<Plot> out = new ArrayList<>(coords.size());
        for (Coord c : coords) {
            if (board.getPlotAtPos(c.row(), c.col()) instanceof Plot plot) out.add(plot);
        }
        return out;
    }
}
