package io.github.elderpath_crusade.abilities.data;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.abilities.stats.StatsModifier;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.IdentityComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.ecs.components.StunComponent;
import io.github.elderpath_crusade.ecs.systems.CombatSystem;
import io.github.elderpath_crusade.ecs.systems.MovementSystem;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.PieceMovedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EffectExecutor {

    private static final ComponentMapper<StatsComponent> statsMapper = ComponentMapper.getFor(StatsComponent.class);
    private static final ComponentMapper<PositionComponent> posMapper = ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<IdentityComponent> idMapper = ComponentMapper.getFor(IdentityComponent.class);
    private static final ComponentMapper<AlignmentComponent> alignMapper = ComponentMapper.getFor(AlignmentComponent.class);

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
            default -> {}
        }
    }

    private static void executeDamage(EffectNode effect, List<Entity> targets, ExpressionContext context) {
        int amount = ExpressionEvaluator.evaluateInt(effect.params().get("amount"), context);
        if (amount <= 0) return;
        CombatSystem combat = GameContext.get().getEcsEngine().getSystem(CombatSystem.class);
        for (Entity target : targets) {
            combat.applyDamage(target, amount);
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

        if ("AwayFromSelf".equals(destination)) {
            PositionComponent ownerPos = posMapper.get(owner);
            if (ownerPos == null) return;
            for (Entity target : targets) {
                PositionComponent targetPos = posMapper.get(target);
                if (targetPos == null) continue;
                int dRow = targetPos.row - ownerPos.row;
                int dCol = targetPos.col - ownerPos.col;
                if (dRow != 0) dRow = dRow > 0 ? 1 : -1;
                if (dCol != 0) dCol = dCol > 0 ? 1 : -1;
                int pushRow = targetPos.row + dRow;
                int pushCol = targetPos.col + dCol;
                movement.executeForcedMove(target, pushRow, pushCol, "ABILITY", "PushOnAttack");
            }
            return;
        }

        int row = ExpressionEvaluator.evaluateInt(effect.params().get("row"), context);
        int col = ExpressionEvaluator.evaluateInt(effect.params().get("col"), context);
        for (Entity target : targets) {
            movement.executeForcedMove(target, row, col, "ability", null);
        }
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
        boolean result = evaluateCondition(condObj, context);

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
        Object selectorObj = effect.params().get("targets");
        TargetSelector selector;
        if (selectorObj instanceof TargetSelector ts) {
            selector = ts;
        } else if (selectorObj instanceof String s) {
            selector = new TargetSelector(s);
        } else if (selectorObj instanceof Map<?, ?> map) {
            String type = (String) map.get("type");
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) map.get("params");
            selector = new TargetSelector(type, params);
        } else {
            return;
        }

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
            for (EffectNode node : doEffects) {
                execute(node, List.of(target), owner, context, abilityState);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void executeAddModifier(EffectNode effect, List<Entity> targets) {
        Map<String, Object> params = effect.params();
        // Stats may be nested under a "stats" key (from YAML: {target: ..., stats: {addDamage: 1}})
        Object statsObj = params.get("stats");
        Map<String, Object> stats = (statsObj instanceof Map<?, ?> m) ? (Map<String, Object>) m : params;
        StatsModifier mod = new StatsModifier();
        if (stats.containsKey("addDamage")) mod.addDamage = ((Number) stats.get("addDamage")).intValue();
        if (stats.containsKey("addSpeed")) mod.addSpeed = ((Number) stats.get("addSpeed")).intValue();
        if (stats.containsKey("addActions")) mod.addActions = ((Number) stats.get("addActions")).intValue();
        if (stats.containsKey("addMaxHealth")) mod.addMaxHealth = ((Number) stats.get("addMaxHealth")).intValue();
        if (stats.containsKey("addRange")) mod.addRange = ((Number) stats.get("addRange")).intValue();

        for (Entity target : targets) {
            io.github.elderpath_crusade.ecs.components.ModifierComponent mc = target.getComponent(io.github.elderpath_crusade.ecs.components.ModifierComponent.class);
            if (mc != null) {
                mc.accumulator.add(mod);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean evaluateCondition(Object condObj, ExpressionContext context) {
        if (condObj == null) return false;
        Condition cond;
        if (condObj instanceof Condition c) {
            cond = c;
        } else if (condObj instanceof Map<?, ?> map) {
            cond = new Condition((String) map.get("type"), (Map<String, Object>) map);
        } else {
            return false;
        }

        return switch (cond.type()) {
            case "HealthBelow" -> {
                int threshold = ExpressionEvaluator.evaluateInt(cond.params().get("threshold"), context);
                int health = ExpressionEvaluator.evaluateInt(context.get("$self.health"), context);
                yield health < threshold;
            }
            case "IsEnemy" -> ExpressionEvaluator.evaluateBoolean(context.get("$target.isEnemy"), context);
            case "ModuloEquals" -> {
                int value = ExpressionEvaluator.evaluateInt(cond.params().get("value"), context);
                int divisor = ExpressionEvaluator.evaluateInt(cond.params().get("divisor"), context);
                int remainder = ExpressionEvaluator.evaluateInt(cond.params().get("remainder"), context);
                yield divisor != 0 && (value % divisor) == remainder;
            }
            default -> false;
        };
    }

    @SuppressWarnings("unchecked")
    private static List<EffectNode> toEffectNodes(Object obj) {
        if (!(obj instanceof List<?> list)) return List.of();
        List<EffectNode> nodes = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof EffectNode en) nodes.add(en);
            else if (item instanceof Map<?, ?> map) {
                nodes.add(new EffectNode((String) map.get("type"), (Map<String, Object>) map));
            }
        }
        return nodes;
    }
}
