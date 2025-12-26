package io.github.elderpath_crusade.bot.eval;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.game_piece.GamePiece;
import io.github.elderpath_crusade.game_objects.board.game_piece.MonsterGamePiece;
import io.github.elderpath_crusade.game_objects.board.plot.Plot;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.managers.PlayerManager;

import io.github.elderpath_crusade.bot.eval.BotActionContext.PieceEntry;

import java.util.HashMap;
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

    protected int getRemainingActions(MonsterGamePiece piece) {
        return piece.getStats().getRemainingActions();
    }

    protected boolean canAct(MonsterGamePiece piece) {
        if (piece == null)
            return false;
        if (piece.isStunned())
            return false;
        return getRemainingActions(piece) > 0;
    }

    protected boolean inBounds(Board board, int row, int col) {
        return row >= 0 && row < board.getROWS() && col >= 0 && col < board.getCOLS();
    }

    protected boolean moveAndVerify(Board board, int sourceRow, int sourceCol, Plot destination, GamePiece reference,
            int targetRow, int targetCol) {
        Renderable sourceRenderable = board.getPlotAtPos(sourceRow, sourceCol);
        if (!(sourceRenderable instanceof Plot sourcePlot))
            return false;
        HashMap<Integer, CustomBox> entities = new HashMap<>();
        entities.put(0, sourcePlot);
        entities.put(1, destination);
        sourcePlot.triggerClickEffect(entities);
        GamePiece pieceAfter = board.getGamePieceAtPos(targetRow, targetCol);
        return pieceAfter == reference;
    }

    protected boolean attackAndVerify(Board board, int sourceRow, int sourceCol, int targetRow, int targetCol,
            MonsterGamePiece attacker) {
        Renderable sourceRenderable = board.getPlotAtPos(sourceRow, sourceCol);
        Renderable targetRenderable = board.getPlotAtPos(targetRow, targetCol);
        if (!(sourceRenderable instanceof Plot sourcePlot) || !(targetRenderable instanceof Plot targetPlot))
            return false;

        GamePiece defenderBefore = board.getGamePieceAtPos(targetRow, targetCol);
        int actionsBefore = getRemainingActions(attacker);

        HashMap<Integer, CustomBox> entities = new HashMap<>();
        entities.put(0, sourcePlot);
        entities.put(1, targetPlot);
        sourcePlot.triggerClickEffect(entities);

        GamePiece defenderAfter = board.getGamePieceAtPos(targetRow, targetCol);
        if (defenderBefore != null && (defenderAfter == null || defenderAfter != defenderBefore))
            return true; // killed or moved

        int actionsAfter = getRemainingActions(attacker);
        return actionsAfter < actionsBefore; // action spent implies a hit
    }

    protected boolean isLethalThreatNextTurn(Board board, MonsterGamePiece piece, int row, int col) {
        int rows = board.getROWS();
        int cols = board.getCOLS();
        int health = Math.max(0, piece.getStats().getCurrentHealth());
        PieceAlignment enemyAlignment = (piece.getAlignment() == PieceAlignment.P1) ? PieceAlignment.P2
                : PieceAlignment.P1;

        for (int enemyRow = 0; enemyRow < rows; enemyRow++) {
            for (int enemyCol = 0; enemyCol < cols; enemyCol++) {
                GamePiece enemyPiece = board.getGamePieceAtPos(enemyRow, enemyCol);
                if (!(enemyPiece instanceof MonsterGamePiece enemy) || enemy.getAlignment() != enemyAlignment) {
                    continue;
                }

                int damage = enemy.getEffectiveDamage();
                if (damage <= 0) {
                    continue;
                }

                int actions = enemy.getEffectiveActions();
                if (actions <= 0) {
                    continue;
                }

                int speed = enemy.getStats().getSpeed();
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

                        if (board.getGamePieceAtPos(nextRow, nextCol) != null) {
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

    protected boolean summonAndVerify(Board board, SummonCard card, Plot plot) {
        var player = PlayerManager.get(PieceAlignment.P2);
        if (player == null || player.hand == null)
            return false;
        int beforeSize = player.hand.getCards().size();
        int beforeMana = player.mana;
        HashMap<Integer, CustomBox> entities = new HashMap<>();
        entities.put(0, card);
        entities.put(1, plot);
        card.triggerClickEffect(entities);
        boolean consumed = !player.hand.getCards().contains(card) || player.hand.getCards().size() < beforeSize;
        boolean spentMana = player.mana < beforeMana;
        return consumed || spentMana;
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

    protected boolean hasAbility(MonsterGamePiece piece, String abilityName) {
        if (piece == null)
            return false;
        try {
            for (var ability : piece.getAbilities()) {
                if (ability != null && ability.getClass().getSimpleName().equals(abilityName)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    protected boolean isRogue(MonsterGamePiece piece) {
        return hasAbility(piece, "RogueFreeStrikeAbility");
    }
}
