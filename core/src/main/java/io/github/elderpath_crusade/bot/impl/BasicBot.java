package io.github.elderpath_crusade.bot.impl;

import io.github.elderpath_crusade.match.CardInstance;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
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

    private PieceAlignment self = PieceAlignment.P2;
    private PieceAlignment enemy = PieceAlignment.P1;

    @Override
    public String getName() { return "BasicBot"; }

    @Override
    public void onTurnStarted(PieceAlignment player) {
        if (player != PieceAlignment.P1 && player != PieceAlignment.P2) return;
        self = player;
        enemy = player == PieceAlignment.P1 ? PieceAlignment.P2 : PieceAlignment.P1;
        chainNextAction(0);
    }

    private void chainNextAction(int actionsDone) {
        if (GameContext.get().getGameManager().isPaused() || GameContext.get().getTurnManager().getCurrentPlayer() != self) return;
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
        GameContext.get().getMatchClock().schedule(DELAY_BETWEEN_ACTIONS, () -> chainNextAction(actionsDone));
    }

    private void scheduleEndTurn() {
        PieceAlignment endingFor = self;
        GameContext.get().getMatchClock().schedule(DELAY_BEFORE_END, () -> {
            // GameServer only accepts it if it's still endingFor's turn
            if (!GameContext.get().getGameManager().isPaused())
                GameContext.get().getGameServer().endTurn(endingFor);
        });
    }

    private boolean tryPlayOneWolfCard(BoardGrid b) {
        var ps = GameContext.get().getPlayerManager().get(self);
        if (ps == null) return false;
        CardInstance targetCard = null;
        for (CardInstance c : ps.hand.getCards()) { if ("Wolf".equals(c.cardName())) { targetCard = c; break; } }
        if (targetCard == null) return false;
        if (!GameContext.get().getCardRules().canPlay(self, targetCard.cardName())) return false;

        int homeRow = b.getHomeRow(self);
        for (int col = 0; col < b.getCols(); col++) {
            if (b.isValidSummonTarget(homeRow, col, self)
                    && GameContext.get().getGameServer().playSummonCard(self, targetCard.instanceId(), homeRow, col).isOk()) return true;
        }
        return false;
    }

    private boolean tryOneAdjacentAttack(BoardGrid b) {
        int rows = b.getRows(), cols = b.getCols();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Entity entity = b.getEntityAt(r, c);
                if (entity != null && EntityUtils.getAlignment(entity) == self && canAct(entity)) {
                    List<Coord> hostile = b.getAttackableEnemies(r, c, self);
                    if (!hostile.isEmpty()) {
                        Coord dst = hostile.get(0);
                        Entity defenderBefore = b.getEntityAt(dst.row(), dst.col());
                        int actionsBefore = EntityUtils.getRemainingActions(entity);
                        GameContext.get().getGameServer().movePlot(self, r, c, dst.row(), dst.col());
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
            if (e != null && EntityUtils.getAlignment(e) == enemy) enemies.add(new int[]{r,c});
        }
        if (enemies.isEmpty()) return false;
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            Entity entity = b.getEntityAt(r, c);
            if (entity == null || EntityUtils.getAlignment(entity) != self || !canAct(entity)) continue;
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
                GameContext.get().getGameServer().movePlot(self, r, c, best.row(), best.col());
                if (b.getEntityAt(best.row(), best.col()) == entity) return true;
            }
        }
        return false;
    }

    private static boolean canAct(Entity entity) {
        return !EntityUtils.isExhausted(entity) && !EntityUtils.isStunned(entity);
    }

    private int nearestEnemyManhattan(int r, int c, List<int[]> enemies) {
        int best = Integer.MAX_VALUE; for (int[] e : enemies) { int d = Math.abs(e[0]-r)+Math.abs(e[1]-c); if (d<best) best=d; }
        return best;
    }
}
