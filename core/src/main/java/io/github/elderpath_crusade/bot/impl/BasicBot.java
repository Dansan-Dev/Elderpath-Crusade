package io.github.elderpath_crusade.bot.impl;

import io.github.elderpath_crusade.GameContext;
import com.badlogic.gdx.utils.Timer;
import io.github.elderpath_crusade.cards.WolfCard;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.bot.Bot;
import io.github.elderpath_crusade.utils.Logger;

import java.util.ArrayList;
import java.util.HashMap;
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

        Board board = GameContext.get().getActiveBoard();
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

    private boolean tryPlayOneWolfCard(Board b) {
        var ps = GameContext.get().getPlayerManager().get(PieceAlignment.P2);
        if (ps == null || ps.hand == null) return false;
        WolfCard targetCard = null;
        for (var c : ps.hand.getCards()) { if (c instanceof WolfCard wc) { targetCard = wc; break; } }
        if (targetCard == null) return false;

        int lastRow = b.getROWS() - 1; // P2 home row
        for (int col = 0; col < b.getCOLS(); col++) {
            Renderable r = b.getPlotAtPos(lastRow, col);
            if (r instanceof Plot p && b.isValidSummonTarget(p, PieceAlignment.P2)) {
                int beforeHandSize = ps.hand.getCards().size();
                int beforeMana = ps.mana;
                HashMap<Integer, CustomBox> entities = new HashMap<>();
                entities.put(0, targetCard);
                entities.put(1, p);
                targetCard.triggerClickEffect(entities);
                boolean consumed = !ps.hand.getCards().contains(targetCard) || ps.hand.getCards().size() < beforeHandSize;
                boolean spentMana = ps.mana < beforeMana;
                if (consumed || spentMana) return true;
            }
        }
        return false;
    }

    private boolean tryOneAdjacentAttack(Board b) {
        int rows = b.getROWS(), cols = b.getCOLS();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                GamePiece gp = b.getGamePieceAtPos(r, c);
                if (gp instanceof MonsterGamePiece mgp && mgp.getAlignment() == PieceAlignment.P2) {
                    List<Plot> hostile = b.getAttackableEnemyPlots(r, c, PieceAlignment.P2);
                    if (!hostile.isEmpty()) {
                        Renderable srcR = b.getPlotAtPos(r, c);
                        if (srcR instanceof Plot srcPlot) {
                            Plot dstPlot = hostile.get(0);
                            int[] dIdx = dstPlot.getIndices();
                            if (dIdx == null) continue;
                            GamePiece defenderBefore = b.getGamePieceAtPos(dIdx[0], dIdx[1]);
                            int defenderHpBefore = -1;
                            if (defenderBefore instanceof MonsterGamePiece defM) defenderHpBefore = defM.getStats().getCurrentHealth();
                            int actionsBefore = mgp.getStats().getRemainingActions();
                            HashMap<Integer, CustomBox> entities = new HashMap<>();
                            entities.put(0, srcPlot); entities.put(1, dstPlot);
                            srcPlot.triggerClickEffect(entities);
                            GamePiece defenderAfter = b.getGamePieceAtPos(dIdx[0], dIdx[1]);
                            boolean defenderDied = (defenderBefore instanceof MonsterGamePiece) && (defenderAfter == null || defenderAfter != defenderBefore);
                            boolean defenderDamaged = false;
                            if (defenderBefore instanceof MonsterGamePiece defM2 && defenderAfter instanceof MonsterGamePiece defM2After && defenderBefore == defenderAfter) {
                                defenderDamaged = defM2After.getStats().getCurrentHealth() < defenderHpBefore;
                            }
                            int actionsAfter = mgp.getStats().getRemainingActions();
                            boolean spentAction = actionsAfter < actionsBefore;
                            if (defenderDied || defenderDamaged || spentAction) return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean tryOneMovementTowardEnemy(Board b) {
        int rows = b.getROWS(), cols = b.getCOLS();
        List<int[]> enemies = new ArrayList<>();
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            GamePiece gp = b.getGamePieceAtPos(r, c);
            if (gp instanceof MonsterGamePiece em && em.getAlignment() == PieceAlignment.P1) enemies.add(new int[]{r,c});
        }
        if (enemies.isEmpty()) return false;
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            GamePiece gp = b.getGamePieceAtPos(r, c);
            if (!(gp instanceof MonsterGamePiece mgp) || mgp.getAlignment() != PieceAlignment.P2) continue;
            int currentDist = nearestEnemyManhattan(r,c,enemies);
            if (currentDist <= 1) continue;
            int speed = mgp.getStats().getSpeed();
            List<Plot> reachable = b.getReachablePlots(r,c,speed);
            if (reachable.isEmpty()) continue;
            Plot best = null; int bestDist = currentDist;
            for (Plot p : reachable) {
                int[] idx = p.getIndices(); if (idx == null) continue;
                int d = nearestEnemyManhattan(idx[0], idx[1], enemies);
                if (d < bestDist) { bestDist = d; best = p; }
            }
            if (best != null) {
                Renderable srcR = b.getPlotAtPos(r,c);
                if (srcR instanceof Plot srcPlot) {
                    int[] bestIdx = best.getIndices(); if (bestIdx == null) continue;
                    GamePiece before = b.getGamePieceAtPos(r,c);
                    HashMap<Integer, CustomBox> entities = new HashMap<>();
                    entities.put(0, srcPlot); entities.put(1, best);
                    srcPlot.triggerClickEffect(entities);
                    GamePiece afterAtDest = b.getGamePieceAtPos(bestIdx[0], bestIdx[1]);
                    if (afterAtDest == before) return true;
                }
            }
        }
        return false;
    }

    private int nearestEnemyManhattan(int r, int c, List<int[]> enemies) {
        int best = Integer.MAX_VALUE; for (int[] e : enemies) { int d = Math.abs(e[0]-r)+Math.abs(e[1]-c); if (d<best) best=d; }
        return best;
    }
}
