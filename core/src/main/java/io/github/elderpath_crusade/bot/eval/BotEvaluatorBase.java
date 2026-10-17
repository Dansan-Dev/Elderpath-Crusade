package io.github.elderpath_crusade.bot.eval;

import io.github.elderpath_crusade.match.CardInstance;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.ecs.components.AbilityInstanceComponent;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.BoardGrid;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.game.PlayerManager;

import io.github.elderpath_crusade.bot.eval.BotActionContext.PieceEntry;

import java.util.List;

/**
 * Base class for bot evaluators containing shared constants and utility
 * methods.
 */
public abstract class BotEvaluatorBase implements IntentGenerator {

    protected final BotConfig config;

    public BotEvaluatorBase(BotConfig config) {
        this.config = config;
    }

    protected int getRemainingActions(Entity entity) {
        return EntityUtils.getRemainingActions(entity);
    }

    protected boolean canAct(Entity entity) {
        if (entity == null)
            return false;
        if (EntityUtils.isStunned(entity))
            return false;
        return getRemainingActions(entity) > 0;
    }

    protected boolean inBounds(BoardGrid board, int row, int col) {
        return row >= 0 && row < board.getRows() && col >= 0 && col < board.getCols();
    }

    protected boolean moveAndVerify(BoardGrid board, int sourceRow, int sourceCol, Coord destination, Entity reference,
            int targetRow, int targetCol) {
        if (destination == null)
            return false;
        GameContext.get().getGameServer().movePlot(sourceRow, sourceCol, destination.row(), destination.col());
        Entity entityAfter = board.getEntityAt(targetRow, targetCol);
        return entityAfter == reference;
    }

    protected boolean attackAndVerify(BoardGrid board, int sourceRow, int sourceCol, int targetRow, int targetCol,
            Entity attacker) {
        if (!board.isInBounds(sourceRow, sourceCol) || !board.isInBounds(targetRow, targetCol))
            return false;

        Entity defenderBefore = board.getEntityAt(targetRow, targetCol);
        int actionsBefore = getRemainingActions(attacker);

        GameContext.get().getGameServer().movePlot(sourceRow, sourceCol, targetRow, targetCol);

        Entity defenderAfter = board.getEntityAt(targetRow, targetCol);
        if (defenderBefore != null && (defenderAfter == null || defenderAfter != defenderBefore))
            return true; // killed or moved

        int actionsAfter = getRemainingActions(attacker);
        return actionsAfter < actionsBefore; // action spent implies a hit
    }

    protected boolean isLethalThreatNextTurn(BoardGrid board, Entity entity, int row, int col) {
        int rows = board.getRows();
        int cols = board.getCols();
        int health = Math.max(0, EntityUtils.getCurrentHealth(entity));
        PieceAlignment enemyAlignment = (EntityUtils.getAlignment(entity) == PieceAlignment.P1) ? PieceAlignment.P2
                : PieceAlignment.P1;

        for (int enemyRow = 0; enemyRow < rows; enemyRow++) {
            for (int enemyCol = 0; enemyCol < cols; enemyCol++) {
                Entity enemy = board.getEntityAt(enemyRow, enemyCol);
                if (enemy == null || EntityUtils.getAlignment(enemy) != enemyAlignment) {
                    continue;
                }

                int damage = EntityUtils.getDamage(enemy);
                if (damage <= 0) {
                    continue;
                }

                int actions = EntityUtils.getActions(enemy);
                if (actions <= 0) {
                    continue;
                }

                int speed = EntityUtils.getSpeed(enemy);
                int maxReach = Math.max(1, (actions - 1) * speed + 1);

                java.util.Queue<int[]> queue = new java.util.ArrayDeque<>();
                boolean[][] visited = new boolean[rows][cols];
                queue.add(new int[] { enemyRow, enemyCol, 0 });
                visited[enemyRow][enemyCol] = true;

                while (!queue.isEmpty()) {
                    int[] current = queue.poll();
                    int currentRow = current[0];
                    int currentCol = current[1];
                    int distance = current[2];

                    if (distance > maxReach) {
                        continue;
                    }

                    // If from (currentRow, currentCol) the enemy can attack (row, col) (adjacent)
                    if (Math.abs(currentRow - row) + Math.abs(currentCol - col) == 1) {
                        if (damage >= health) {
                            return true;
                        }
                    }

                    if (distance == maxReach) {
                        continue;
                    }

                    int[][] directions = new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
                    for (int[] direction : directions) {
                        int nextRow = currentRow + direction[0];
                        int nextCol = currentCol + direction[1];

                        if (nextRow < 0 || nextCol < 0 || nextRow >= rows || nextCol >= cols
                                || visited[nextRow][nextCol]) {
                            continue;
                        }

                        if (board.getEntityAt(nextRow, nextCol) != null) {
                            continue; // Cannot pass through pieces
                        }

                        visited[nextRow][nextCol] = true;
                        queue.add(new int[] { nextRow, nextCol, distance + 1 });
                    }
                }
            }
        }
        return false;
    }

    protected boolean summonAndVerify(PieceAlignment self, CardInstance card, Coord target) {
        return GameContext.get().getGameServer().playSummonCard(
                self, card.instanceId(), target.row(), target.col());
    }

    /** +1 if moving from fromRow to toRow advances toward self's goal row, -1 if it retreats, 0 if sideways. */
    protected int forwardness(BoardGrid board, PieceAlignment self, int fromRow, int toRow) {
        int forward = board.getGoalRow(self) > board.getHomeRow(self) ? 1 : -1;
        return Integer.signum((toRow - fromRow) * forward);
    }

    protected int nearestManhattan(int row, int col, List<PieceEntry> targets) {
        int minDistance = Integer.MAX_VALUE;
        for (PieceEntry entry : targets) {
            int distance = Math.abs(row - entry.pos().row()) + Math.abs(col - entry.pos().col());
            if (distance < minDistance) {
                minDistance = distance;
            }
        }
        return minDistance;
    }

    protected boolean hasAbility(Entity entity, String abilityId) {
        if (entity == null)
            return false;
        try {
            AbilityInstanceComponent aic = entity.getComponent(AbilityInstanceComponent.class);
            if (aic != null) {
                for (var def : aic.definitions) {
                    if (def != null && def.id().equals(abilityId)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    protected boolean isRogue(Entity entity) {
        return hasAbility(entity, "RogueFreeStrike");
    }
}
