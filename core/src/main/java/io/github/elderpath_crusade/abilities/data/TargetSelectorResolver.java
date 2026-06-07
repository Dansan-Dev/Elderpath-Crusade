package io.github.elderpath_crusade.abilities.data;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.systems.GridIndexSystem;
import io.github.elderpath_crusade.enums.PieceAlignment;

import java.util.ArrayList;
import java.util.List;

public class TargetSelectorResolver {

    private static final ComponentMapper<PositionComponent> posMapper = ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<AlignmentComponent> alignMapper = ComponentMapper.getFor(AlignmentComponent.class);
    private static final int[][] CARDINAL = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    public static List<Entity> resolve(TargetSelector selector, Entity owner, ExpressionContext context) {
        return switch (selector.type()) {
            case "Self" -> List.of(owner);
            case "AdjacentEnemies" -> getAdjacentByAlignment(owner, true);
            case "AdjacentFriendlyUnits" -> getAdjacentByAlignment(owner, false);
            case "AllEnemyUnits" -> getAllByAlignment(owner, true);
            case "AllFriendlyUnits" -> getAllByAlignment(owner, false);
            case "NearestEnemy" -> getNearestByAlignment(owner, true);
            case "NearestAdjacentEnemy" -> {
                List<Entity> adj = getAdjacentByAlignment(owner, true);
                yield adj.isEmpty() ? List.of() : List.of(adj.get(0));
            }
            case "UnitsInRow" -> getUnitsInRow(owner, selector, context);
            case "AdjacentUnitsAt" -> getAdjacentByAlignmentAt(owner, selector, context);
            case "NextInLine" -> getNextInLine(owner, context);
            default -> List.of();
        };
    }

    private static List<Entity> getAdjacentByAlignment(Entity owner, boolean enemies) {
        PositionComponent ownerPos = posMapper.get(owner);
        AlignmentComponent ownerAlign = alignMapper.get(owner);
        if (ownerPos == null || ownerAlign == null) return List.of();

        GridIndexSystem grid = GameContext.get().getEcsEngine().getSystem(GridIndexSystem.class);
        List<Entity> result = new ArrayList<>();

        for (int[] dir : CARDINAL) {
            Entity neighbor = grid.getEntityAt(ownerPos.row + dir[0], ownerPos.col + dir[1]);
            if (neighbor == null) continue;
            AlignmentComponent neighborAlign = alignMapper.get(neighbor);
            if (neighborAlign == null) continue;
            boolean isEnemy = neighborAlign.alignment != ownerAlign.alignment;
            if (isEnemy == enemies) result.add(neighbor);
        }
        return result;
    }

    private static List<Entity> getAllByAlignment(Entity owner, boolean enemies) {
        AlignmentComponent ownerAlign = alignMapper.get(owner);
        if (ownerAlign == null) return List.of();

        Engine engine = GameContext.get().getEcsEngine();
        ImmutableArray<Entity> all = engine.getEntitiesFor(Family.all(AlignmentComponent.class, PositionComponent.class).get());
        List<Entity> result = new ArrayList<>();

        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            if (e == owner) continue;
            AlignmentComponent align = alignMapper.get(e);
            boolean isEnemy = align.alignment != ownerAlign.alignment;
            if (isEnemy == enemies) result.add(e);
        }
        return result;
    }

    private static List<Entity> getNearestByAlignment(Entity owner, boolean enemies) {
        PositionComponent ownerPos = posMapper.get(owner);
        if (ownerPos == null) return List.of();

        List<Entity> candidates = getAllByAlignment(owner, enemies);
        Entity nearest = null;
        int minDist = Integer.MAX_VALUE;

        for (Entity e : candidates) {
            PositionComponent pos = posMapper.get(e);
            if (pos == null) continue;
            int dist = Math.abs(pos.row - ownerPos.row) + Math.abs(pos.col - ownerPos.col);
            if (dist < minDist) {
                minDist = dist;
                nearest = e;
            }
        }
        return nearest != null ? List.of(nearest) : List.of();
    }

    private static List<Entity> getUnitsInRow(Entity owner, TargetSelector selector, ExpressionContext context) {
        Object rowParam = selector.params().get("row");
        int row;
        if (rowParam != null) {
            row = ExpressionEvaluator.evaluateInt(rowParam, context);
        } else {
            PositionComponent ownerPos = posMapper.get(owner);
            if (ownerPos == null) return List.of();
            row = ownerPos.row;
        }

        Engine engine = GameContext.get().getEcsEngine();
        ImmutableArray<Entity> all = engine.getEntitiesFor(Family.all(PositionComponent.class).get());
        List<Entity> result = new ArrayList<>();

        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            PositionComponent pos = posMapper.get(e);
            if (pos.row == row) result.add(e);
        }
        return result;
    }

    private static List<Entity> getNextInLine(Entity owner, ExpressionContext context) {
        PositionComponent ownerPos = posMapper.get(owner);
        if (ownerPos == null) return List.of();

        int victimRow = ExpressionEvaluator.evaluateInt(context.get("$event.row"), context);
        int victimCol = ExpressionEvaluator.evaluateInt(context.get("$event.col"), context);

        int dRow = victimRow - ownerPos.row;
        int dCol = victimCol - ownerPos.col;
        if (dRow != 0) dRow = dRow > 0 ? 1 : -1;
        if (dCol != 0) dCol = dCol > 0 ? 1 : -1;

        GridIndexSystem grid = GameContext.get().getEcsEngine().getSystem(GridIndexSystem.class);
        Entity next = grid.getEntityAt(victimRow + dRow, victimCol + dCol);
        return next != null ? List.of(next) : List.of();
    }

    private static List<Entity> getAdjacentByAlignmentAt(Entity owner, TargetSelector selector, ExpressionContext context) {
        AlignmentComponent ownerAlign = alignMapper.get(owner);
        if (ownerAlign == null) return List.of();

        // Get center position from context variables or selector params
        Object rowParam = selector.params() != null ? selector.params().get("row") : null;
        Object colParam = selector.params() != null ? selector.params().get("col") : null;
        int centerRow = ExpressionEvaluator.evaluateInt(rowParam != null ? rowParam : context.get("$event.defenderRow"), context);
        int centerCol = ExpressionEvaluator.evaluateInt(colParam != null ? colParam : context.get("$event.defenderCol"), context);

        // Get alignment filter from params
        String alignmentFilter = selector.params() != null ? (String) selector.params().get("alignment") : null;
        boolean findEnemies = "Enemy".equals(alignmentFilter);
        boolean findFriendly = "Friendly".equals(alignmentFilter);

        GridIndexSystem grid = GameContext.get().getEcsEngine().getSystem(GridIndexSystem.class);
        List<Entity> result = new ArrayList<>();

        for (int[] dir : CARDINAL) {
            Entity neighbor = grid.getEntityAt(centerRow + dir[0], centerCol + dir[1]);
            if (neighbor == null || neighbor == owner) continue;
            AlignmentComponent neighborAlign = alignMapper.get(neighbor);
            if (neighborAlign == null) continue;
            boolean isEnemy = neighborAlign.alignment != ownerAlign.alignment;
            if (findEnemies && isEnemy) result.add(neighbor);
            else if (findFriendly && !isEnemy) result.add(neighbor);
            else if (!findEnemies && !findFriendly) result.add(neighbor); // all units at position
        }
        return result;
    }
}
