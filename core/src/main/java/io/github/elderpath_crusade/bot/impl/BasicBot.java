package io.github.elderpath_crusade.bot.impl;

import io.github.elderpath_crusade.match.CardInstance;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import com.badlogic.gdx.utils.Timer;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.BoardGrid;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.bot.Bot;
import io.github.elderpath_crusade.utils.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Straight port of the previous simple bot.
 * Priority: ATTACK > MOVE > SUMMON (WolfCard). Repeats until no action then ends turn.
 */
public class BasicBot implements Bot {
    private static final float DELAY_BETWEEN_ACTIONS = 0.35f;
    private static final float DELAY_BEFORE_END = 0.4f;
    private static final int MAX_ACTIONS_PER_TURN = 50;

    @Override
    public String getName() { return "BasicBot"; }

    @Override
    public void onTurnStarted(PieceAlignment player) {
        if (player != PieceAlignment.P2) return;
        chainNextAction(0);
    }

    private void chainNextAction(int actionsDone) {
        if (GameContext.get().getGameManager().isPaused() || GameContext.get().getTurnManager().getCurrentPlayer() != PieceAlignment.P2) return;
        if (actionsDone >= MAX_ACTIONS_PER_TURN) { Logger.log("BasicBot", "Max actions cap; ending turn"); scheduleEndTurn(); return; }

        BoardGrid board = GameContext.get().getActiveGrid();
        if (board == null) { Logger.log("BasicBot", "No board; end turn"); scheduleEndTurn(); return; }

        if (tryOneAdjacentAttack(board)) { Logger.log("BasicBot", "ATTACK"); scheduleNextAction(actionsDone+1); return; }
        if (tryOneMovementTowardEnemy(board)) { Logger.log("BasicBot", "MOVE"); scheduleNextAction(actionsDone+1); return; }
        if (tryPlayOneWolfCard(board)) { Logger.log("BasicBot", "SUMMON"); scheduleNextAction(actionsDone+1); return; }

        Logger.log("BasicBot", "No more actions; end turn");
        scheduleEndTurn();
    }

    private void scheduleNextAction(final int actionsDone) {
        Timer.schedule(new Timer.Task() { @Override public void run() { chainNextAction(actionsDone); } }, DELAY_BETWEEN_ACTIONS);
    }

    private void scheduleEndTurn() {
        Timer.schedule(new Timer.Task() {
            @Override public void run() { if (!GameContext.get().getGameManager().isPaused()) GameContext.get().getTurnManager().endTurn(); }
        }, DELAY_BEFORE_END);
    }

    private boolean tryPlayOneWolfCard(BoardGrid b) {
        var ps = GameContext.get().getPlayerManager().get(PieceAlignment.P2);
        if (ps == null) return false;
        CardInstance targetCard = null;
        for (CardInstance c : ps.hand.getCards()) { if ("Wolf".equals(c.cardName())) { targetCard = c; break; } }
        if (targetCard == null) return false;

        int homeRow = b.getHomeRow(PieceAlignment.P2);
        for (int col = 0; col < b.getCols(); col++) {
            if (b.isValidSummonTarget(homeRow, col, PieceAlignment.P2)
                    && GameContext.get().getGameServer().playSummonCard(PieceAlignment.P2, targetCard.instanceId(), homeRow, col)) return true;
        }
        return false;
    }

    private boolean tryOneAdjacentAttack(BoardGrid b) {
        int rows = b.getRows(), cols = b.getCols();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Entity entity = b.getEntityAt(r, c);
                if (entity != null && EntityUtils.getAlignment(entity) == PieceAlignment.P2) {
                    List<Coord> hostile = b.getAttackableEnemies(r, c, PieceAlignment.P2);
                    if (!hostile.isEmpty()) {
                        Coord dst = hostile.get(0);
                        Entity defenderBefore = b.getEntityAt(dst.row(), dst.col());
                        int actionsBefore = EntityUtils.getRemainingActions(entity);
                        GameContext.get().getGameServer().movePlot(r, c, dst.row(), dst.col());
                        Entity defenderAfter = b.getEntityAt(dst.row(), dst.col());
                        boolean defenderDied = (defenderBefore != null) && (defenderAfter == null || defenderAfter != defenderBefore);
                        int actionsAfter = EntityUtils.getRemainingActions(entity);
                        boolean spentAction = actionsAfter < actionsBefore;
                        if (defenderDied || spentAction) return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean tryOneMovementTowardEnemy(BoardGrid b) {
        int rows = b.getRows(), cols = b.getCols();
        List<int[]> enemies = new ArrayList<>();
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            Entity e = b.getEntityAt(r, c);
            if (e != null && EntityUtils.getAlignment(e) == PieceAlignment.P1) enemies.add(new int[]{r,c});
        }
        if (enemies.isEmpty()) return false;
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            Entity entity = b.getEntityAt(r, c);
            if (entity == null || EntityUtils.getAlignment(entity) != PieceAlignment.P2) continue;
            int currentDist = nearestEnemyManhattan(r,c,enemies);
            if (currentDist <= 1) continue;
            int speed = EntityUtils.getSpeed(entity);
            List<Coord> reachable = b.getReachable(entity,r,c,speed);
            if (reachable.isEmpty()) continue;
            Coord best = null; int bestDist = currentDist;
            for (Coord p : reachable) {
                int d = nearestEnemyManhattan(p.row(), p.col(), enemies);
                if (d < bestDist) { bestDist = d; best = p; }
            }
            if (best != null) {
                GameContext.get().getGameServer().movePlot(r, c, best.row(), best.col());
                if (b.getEntityAt(best.row(), best.col()) == entity) return true;
            }
        }
        return false;
    }

    private int nearestEnemyManhattan(int r, int c, List<int[]> enemies) {
        int best = Integer.MAX_VALUE; for (int[] e : enemies) { int d = Math.abs(e[0]-r)+Math.abs(e[1]-c); if (d<best) best=d; }
        return best;
    }
}
