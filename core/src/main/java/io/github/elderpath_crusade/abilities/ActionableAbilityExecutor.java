package io.github.elderpath_crusade.abilities;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
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
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.TargetFilter;
import io.github.elderpath_crusade.interfaces.Renderable;

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
            case "ChooseEnemy" -> startSinglePick(owner, abilityDef, actionDef, ctx, abilityState, true);
            case "ChooseFriendly" -> startSinglePick(owner, abilityDef, actionDef, ctx, abilityState, false);
            case "ChooseRow" -> startRowPick(owner, abilityDef, actionDef, ctx, abilityState);
        }
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

    private static void deductCosts(Entity owner, List<Cost> costs) {
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

    private static void startSinglePick(Entity owner, AbilityDefinition abilityDef, ActionDef actionDef,
            ExpressionContext ctx, Map<String, Object> abilityState, boolean chooseEnemy) {
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
                int[] idx = plot.getIndices();
                int dr = Math.abs(idx[0] - ownerPos.row);
                int dc = Math.abs(idx[1] - ownerPos.col);
                if (Math.max(dr, dc) > range) return false;
                Entity entity = GameContext.get().getActiveBoard().getEntityAtPlot(plot);
                if (entity == null) return false;
                AlignmentComponent align = alignMapper.get(entity);
                if (align == null) return false;
                boolean isEnemy = align.alignment != ownerAlign.alignment;
                return chooseEnemy ? isEnemy : !isEnemy;
            }

            @Override
            public List<Plot> getEligibleTargets(int targetIndex) {
                Board board = GameContext.get().getActiveBoard();
                if (board == null) return List.of();
                List<Plot> eligible = new ArrayList<>();
                for (int r = 0; r < board.getROWS(); r++) {
                    for (int c = 0; c < board.getCOLS(); c++) {
                        Renderable cell = board.getPlotAtPos(r, c);
                        if (!(cell instanceof Plot plot)) continue;
                        int dr = Math.abs(r - ownerPos.row);
                        int dc = Math.abs(c - ownerPos.col);
                        if (Math.max(dr, dc) > range) continue;
                        Entity entity = board.getEntityAtPos(r, c);
                        if (entity == null) continue;
                        AlignmentComponent align = alignMapper.get(entity);
                        if (align == null) continue;
                        boolean isEnemy = align.alignment != ownerAlign.alignment;
                        if (chooseEnemy ? isEnemy : !isEnemy) eligible.add(plot);
                    }
                }
                return eligible;
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
                int[] idx = plot.getIndices();
                int dr = Math.abs(idx[0] - chosenPos.row);
                int dc = Math.abs(idx[1] - chosenPos.col);
                if (Math.max(dr, dc) > 1) return false;
                return GameContext.get().getActiveBoard().getEntityAtPlot(plot) == null;
            }

            @Override
            public List<Plot> getEligibleTargets(int targetIndex) {
                Board board = GameContext.get().getActiveBoard();
                if (board == null) return List.of();
                List<Plot> eligible = new ArrayList<>();
                for (int r = 0; r < board.getROWS(); r++) {
                    for (int c = 0; c < board.getCOLS(); c++) {
                        int dr = Math.abs(r - chosenPos.row);
                        int dc = Math.abs(c - chosenPos.col);
                        if (Math.max(dr, dc) > 1) continue;
                        if (board.getEntityAtPos(r, c) != null) continue;
                        Renderable cell = board.getPlotAtPos(r, c);
                        if (cell instanceof Plot plot) eligible.add(plot);
                    }
                }
                return eligible;
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
}
