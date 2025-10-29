package io.github.elderpath_crusade.managers.bot.impl;

import com.badlogic.gdx.utils.Timer;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.managers.*;
import io.github.elderpath_crusade.managers.bot.Bot;
import io.github.elderpath_crusade.game_objects.cards.Card;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;
import io.github.elderpath_crusade.utils.Logger;

import java.util.*;
import java.util.function.Supplier;

/**
 * Smarter bot implementing a simple urgency-driven policy with safeguards:
 * - Win-opportunity: If any unit can reach the opponent home row (row 0) this turn, do it first.
 * - Adjacent attacks.
 * - BFS-guided movement toward closest enemy while avoiding threatened tiles unless first-strike lethal.
 * - Defensive summoning: consider all SummonCards; prioritize blocking columns where enemies are close to our home row.
 *
 * Notes about coordinates: row 0 is bottom, (ROWS-1) is top. P2 home row = ROWS-1, P1 home row = 0.
 */
public class SmartBot implements Bot {
    private static final float STEP_DELAY = 0.35f;
    private static final float END_DELAY = 0.4f;
    private static final int MAX_STEPS = 60;

    // Per-turn state guards to avoid runaway loops after finishing
    private boolean turnActive = false;
    private boolean ended = false;

    @Override
    public String getName() { return "SmartBot"; }

    @Override
    public void onTurnStarted(PieceAlignment player) {
        if (player != PieceAlignment.P2) return;
        // Initialize per-turn flags
        this.turnActive = true;
        this.ended = false;
        Logger.log("[SmartBot]", "Turn start");
        step(0);
    }

    private void step(int stepsDone) {
        // Abort if turn has ended or not our turn anymore
        if (!turnActive || ended) return;
        if (GraphicsManager.isPaused() || TurnManager.getCurrentPlayer() != PieceAlignment.P2) return;
        if (stepsDone >= MAX_STEPS) { Logger.log("[SmartBot]", "Reached step cap"); endTurn(); return; }
        Board board = getActiveBoard();
        if (board == null) { endTurn(); return; }
        Logger.log("[SmartBot]", "Step tracker: " + stepsDone);
        // If there is nothing left to do, end the turn promptly
        if (shouldEndNow(board)) { Logger.log("[SmartBot]", "Nothing left to do; ending turn"); endTurn(); return; }

        // Intent/ordering engine: build intents, pick highest score, try to execute
        if (executeBestIntent(board)) { scheduleNext(stepsDone+1); return; }

        Logger.log("[SmartBot]", "No more actions; ending turn");
        endTurn();
    }

    private void scheduleNext(final int stepsDone) {
        if (!turnActive || ended) return;
        Timer.schedule(new Timer.Task() { @Override public void run() { step(stepsDone); } }, STEP_DELAY);
    }

    private void endTurn() {
        if (ended) return;
        ended = true;
        turnActive = false;
        Timer.schedule(new Timer.Task() { @Override public void run() { if (!GraphicsManager.isPaused()) TurnManager.endTurn(); } }, END_DELAY);
    }

    private Board getActiveBoard() {
        for (Renderable r : GraphicsManager.getRenderables()) if (r instanceof Board b) return b;
        return null;
    }

    // --- Intent engine ---
    private static class Intent {
        final int score;
        final Supplier<Boolean> exec;
        final String kind;
        Intent(int score, java.util.function.Supplier<Boolean> exec, String kind) {
            this.score = score;
            this.exec = exec;
            this.kind = kind;
        }
    }

    private boolean executeBestIntent(Board b) {
        List<Intent> intents = new ArrayList<>();
        buildWinIntents(b, intents);
        buildAdjacentAttackIntents(b, intents);
        buildAdvanceIntents(b, intents);
        buildSummonIntents(b, intents);
        if (intents.isEmpty()) return false;
        intents.sort((a,bx) -> Integer.compare(bx.score, a.score));
        for (Intent it : intents) {
            try {
                if (it.exec.get()) { Logger.log("[SmartBot]", "Intent executed: " + it.kind + " (score=" + it.score + ")"); return true; }
            } catch (Exception e) { /* ignore and try next */ }
        }
        return false;
    }

    private void buildWinIntents(Board b, List<Intent> out) {
        int rows = b.getROWS(), cols = b.getCOLS();
        int targetRow = 0;
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            GamePiece gp = b.getGamePieceAtPos(r,c);
            if (!(gp instanceof MonsterGamePiece me) || me.getAlignment() != PieceAlignment.P2) continue;
            if (getRemainingActions(me) <= 0) continue;
            int speed = me.getStats().getSpeed();
            List<Plot> reach = b.getReachablePlots(r,c,speed);
            for (Plot p : reach) {
                int[] idx = b.getIndicesOfPlot(p); if (idx == null) continue;
                if (idx[0] == targetRow) {
                    final int sr=r, sc=c; final Plot dest=p; final GamePiece ref=gp;
                    out.add(new Intent(100, () -> moveAndVerify(b, sr, sc, dest, ref, idx[0], idx[1]), "WIN_MOVE"));
                }
            }
        }
    }

    private void buildAdjacentAttackIntents(Board b, List<Intent> out) {
        int rows = b.getROWS(), cols = b.getCOLS();
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            GamePiece gp = b.getGamePieceAtPos(r,c);
            if (!(gp instanceof MonsterGamePiece me) || me.getAlignment() != PieceAlignment.P2) continue;
            if (getRemainingActions(me) <= 0) continue;
            List<Plot> hostile = b.getAdjacentHostilePlots(r,c, PieceAlignment.P2);
            if (hostile == null || hostile.isEmpty()) continue;
            for (Plot dst : hostile) {
                int[] d = b.getIndicesOfPlot(dst); if (d == null) continue;
                GamePiece defender = b.getGamePieceAtPos(d[0], d[1]);
                int base = 70;
                if (defender instanceof MonsterGamePiece em) {
                    int dmg = ((MonsterGamePiece) gp).getStats().getDamage();
                    int hp = em.getStats().getCurrentHealth();
                    if (dmg >= hp) base = 80; // lethal preferred
                    base += Math.min(10, em.getStats().getCost()); // small bump for value
                }
                final int sr=r, sc=c; final Plot target=dst; final MonsterGamePiece att = (MonsterGamePiece) gp;
                final int score = base;
                out.add(new Intent(score, () -> attackAndVerify(b, sr, sc, target, att, d[0], d[1]), "ADJ_ATTACK"));
            }
        }
    }

    private void buildAdvanceIntents(Board b, List<Intent> out) {
        ThreatMap threats = computeThreatMap(b, PieceAlignment.P1);
        int rows = b.getROWS(), cols = b.getCOLS();
        List<int[]> enemies = new ArrayList<>();
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            GamePiece gp = b.getGamePieceAtPos(r,c);
            if (gp instanceof MonsterGamePiece em && em.getAlignment() == PieceAlignment.P1) enemies.add(new int[]{r,c});
        }
        if (enemies.isEmpty()) return;
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            GamePiece gp = b.getGamePieceAtPos(r,c);
            if (!(gp instanceof MonsterGamePiece me) || me.getAlignment() != PieceAlignment.P2) continue;
            if (getRemainingActions(me) <= 0) continue;
            int speed = me.getStats().getSpeed();
            List<Plot> reach = b.getReachablePlots(r,c,speed);
            int currentDist = nearestManhattan(r,c,enemies);
            Plot best = null; int bestDist = currentDist;
            for (Plot p : reach) {
                int[] idx = b.getIndicesOfPlot(p); if (idx == null) continue;
                int d = nearestManhattan(idx[0], idx[1], enemies);
                if (d < bestDist && !threats.isThreatened(idx[0], idx[1])) { best = p; bestDist = d; }
            }
            if (best == null) {
                for (Plot p : reach) {
                    int[] idx = b.getIndicesOfPlot(p); if (idx == null) continue;
                    if (wouldEnableLethal(me, b, idx[0], idx[1])) { best = p; bestDist = nearestManhattan(idx[0], idx[1], enemies); break; }
                }
            }
            if (best != null) {
                final int sr=r, sc=c; final Plot dest=best; final GamePiece ref=gp; int[] bi = b.getIndicesOfPlot(best);
                int gain = Math.max(0, currentDist - bestDist);
                int score = 50 + Math.min(10, gain);
                out.add(new Intent(score, () -> moveAndVerify(b, sr, sc, dest, ref, bi[0], bi[1]), "ADVANCE"));
            }
        }
    }

    private void buildSummonIntents(Board b, List<Intent> out) {
        var ps = PlayerManager.get(PieceAlignment.P2);
        if (ps == null || ps.hand == null) return;
        int mana = ps.mana;
        // Determine threatened column closest to our home row
        int homeRow = b.getROWS() - 1;
        int cols = b.getCOLS();
        int bestCol = -1, bestDist = Integer.MAX_VALUE;
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < b.getROWS(); r++) {
                GamePiece gp = b.getGamePieceAtPos(r,c);
                if (gp instanceof MonsterGamePiece em && em.getAlignment() == PieceAlignment.P1) {
                    int dist = Math.abs(homeRow - r);
                    if (dist < bestDist) { bestDist = dist; bestCol = c; }
                    break;
                }
            }
        }
        // Collect candidate plots (preferred col, else any home-row plot)
        List<Plot> plots = new ArrayList<>();
        if (bestCol != -1) {
            Renderable rp = b.getPlotAtPos(homeRow, bestCol);
            if (rp instanceof Plot p && b.isValidSummonTarget(p, PieceAlignment.P2)) plots.add(p);
        }
        if (plots.isEmpty()) {
            for (int c = 0; c < cols; c++) {
                Renderable rp = b.getPlotAtPos(homeRow, c);
                if (rp instanceof Plot p && b.isValidSummonTarget(p, PieceAlignment.P2)) plots.add(p);
            }
        }
        if (plots.isEmpty()) return;
        // Choose best affordable card using base stats + cost
        SummonCard bestCard = null; int bestScore = Integer.MIN_VALUE;
        for (Card c : ps.hand.getCards()) {
            if (!(c instanceof SummonCard sc)) continue;
            int cost = sc.getManaCost(); if (cost > mana) continue;
            var s = sc.getStats();
            int value = (s.getMaxHealth()*2) + (s.getDamage()*3) + (s.getActions()) + (s.getSpeed()) + (cost); // simple heuristic
            if (value > bestScore) { bestScore = value; bestCard = sc; }
        }
        if (bestCard == null) return;
        Plot dest = plots.get(0);
        final SummonCard card = bestCard; final Plot target = dest;
        int finalScore = 45 + Math.min(30, bestScore/2);
        out.add(new Intent(finalScore, () -> summonAndVerify(b, card, target), "DEF_SUMMON"));
    }

    private boolean moveAndVerify(Board b, int sr, int sc, Plot dest, GamePiece ref, int dr, int dc) {
        Renderable srcR = b.getPlotAtPos(sr, sc);
        if (!(srcR instanceof Plot srcPlot)) return false;
        HashMap<Integer, CustomBox> entities = new HashMap<>();
        entities.put(0, srcPlot); entities.put(1, dest);
        srcPlot.triggerClickEffect(entities);
        GamePiece after = b.getGamePieceAtPos(dr, dc);
        return after == ref;
    }

    private boolean attackAndVerify(Board b, int sr, int sc, Plot dst, MonsterGamePiece attacker, int dr, int dc) {
        Renderable srcR = b.getPlotAtPos(sr, sc);
        if (!(srcR instanceof Plot srcPlot)) return false;
        GamePiece defenderBefore = b.getGamePieceAtPos(dr, dc);
        int actionsBefore = getRemainingActions(attacker);
        HashMap<Integer, CustomBox> entities = new HashMap<>();
        entities.put(0, srcPlot); entities.put(1, dst);
        srcPlot.triggerClickEffect(entities);
        GamePiece defenderAfter = b.getGamePieceAtPos(dr, dc);
        if (defenderBefore != null && defenderAfter != defenderBefore) return true; // killed or moved
        int actionsAfter = getRemainingActions(attacker);
        return actionsAfter < actionsBefore; // action spent implies a hit
    }

    private boolean summonAndVerify(Board b, SummonCard card, Plot p) {
        var ps = PlayerManager.get(PieceAlignment.P2);
        int beforeSize = ps.hand.getCards().size(); int beforeMana = ps.mana;
        HashMap<Integer, CustomBox> entities = new HashMap<>();
        entities.put(0, card); entities.put(1, p);
        card.triggerClickEffect(entities);
        boolean consumed = !ps.hand.getCards().contains(card) || ps.hand.getCards().size() < beforeSize;
        boolean spentMana = ps.mana < beforeMana;
        return consumed || spentMana;
    }

    // --- Turn end checks and helpers ---
    private boolean shouldEndNow(Board b) {
        // If any of our pieces has an actionable option (attack or move) with actions remaining, keep going
        int rows = b.getROWS(), cols = b.getCOLS();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                GamePiece gp = b.getGamePieceAtPos(r, c);
                if (!(gp instanceof MonsterGamePiece me) || me.getAlignment() != PieceAlignment.P2) continue;
                int actions = getRemainingActions(me);
                if (actions <= 0) continue;
                // attack available?
                List<Plot> hostile = b.getAdjacentHostilePlots(r, c, PieceAlignment.P2);
                if (hostile != null && !hostile.isEmpty()) return false;
                // can move somewhere?
                int speed = me.getStats().getSpeed();
                if (speed > 0) {
                    List<Plot> reach = b.getReachablePlots(r, c, speed);
                    if (reach != null && !reach.isEmpty()) return false;
                }
            }
        }
        // No unit can act; if we have a plausible summon candidate, keep going
        if (hasSummonCandidate(b)) return false;
        return true;
    }

    private int getRemainingActions(MonsterGamePiece mgp) {
        Object v = mgp.getData(GamePieceData.ACTIONS_REMAINING);
        if (v instanceof Integer n) return n;
        return mgp.getStats().getActions();
    }

    private boolean hasSummonCandidate(Board b) {
        var ps = PlayerManager.get(PieceAlignment.P2);
        if (ps == null || ps.hand == null) return false;
        int mana = ps.mana;
        boolean hasAffordable = false;
        for (Card c : ps.hand.getCards()) {
            if (c instanceof SummonCard sc && sc.getManaCost() <= mana) { hasAffordable = true; break; }
        }
        if (!hasAffordable) return false;
        int homeRow = b.getROWS() - 1;
        for (int col = 0; col < b.getCOLS(); col++) {
            Renderable rp = b.getPlotAtPos(homeRow, col);
            if (rp instanceof Plot p && b.isValidSummonTarget(p, PieceAlignment.P2)) return true;
        }
        return false;
    }

    // --- Win detection ---
    private boolean tryWinNow(Board b) {
        int rows = b.getROWS(), cols = b.getCOLS();
        int targetRow = 0; // P1 home row
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            GamePiece gp = b.getGamePieceAtPos(r,c);
            if (!(gp instanceof MonsterGamePiece me) || me.getAlignment() != PieceAlignment.P2) continue;
            int speed = me.getStats().getSpeed();
            List<Plot> reach = b.getReachablePlots(r,c,speed);
            for (Plot p : reach) {
                int[] idx = b.getIndicesOfPlot(p); if (idx == null) continue;
                if (idx[0] == targetRow) {
                    // Move there; Board will handle spending 1 action
                    Renderable srcR = b.getPlotAtPos(r,c);
                    if (srcR instanceof Plot srcPlot) {
                        HashMap<Integer, CustomBox> entities = new HashMap<>();
                        entities.put(0, srcPlot);
                        entities.put(1, p);
                        srcPlot.triggerClickEffect(entities);
                        // Validate arrival
                        GamePiece after = b.getGamePieceAtPos(idx[0], idx[1]);
                        if (after == gp) { Logger.log("[SmartBot]", "Win move executed"); return true; }
                    }
                }
            }
        }
        return false;
    }

    // --- Adjacent attack (simple heuristic: first found) ---
    private boolean tryBestAdjacentAttack(Board b) {
        int rows = b.getROWS(), cols = b.getCOLS();
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            GamePiece gp = b.getGamePieceAtPos(r,c);
            if (!(gp instanceof MonsterGamePiece me) || me.getAlignment() != PieceAlignment.P2) continue;
            List<Plot> hostile = b.getAdjacentHostilePlots(r,c, PieceAlignment.P2);
            if (hostile.isEmpty()) continue;
            Renderable srcR = b.getPlotAtPos(r,c);
            if (!(srcR instanceof Plot srcPlot)) continue;
            Plot dst = hostile.get(0);
            int[] d = b.getIndicesOfPlot(dst); if (d == null) continue;
            GamePiece defenderBefore = b.getGamePieceAtPos(d[0], d[1]);
            // Track our actions before triggering to verify that an action was actually spent
            int actionsBefore = getRemainingActions(me);
            HashMap<Integer, CustomBox> entities = new HashMap<>();
            entities.put(0, srcPlot); entities.put(1, dst);
            srcPlot.triggerClickEffect(entities);
            GamePiece defenderAfter = b.getGamePieceAtPos(d[0], d[1]);
            boolean killed = defenderBefore != null && defenderAfter != defenderBefore;
            if (killed) { Logger.log("[SmartBot]", "Attack -> kill"); return true; }
            // If no kill, only treat as a successful step when we actually spent an action (attack executed)
            int actionsAfter = getRemainingActions(me);
            if (actionsAfter < actionsBefore) { Logger.log("[SmartBot]", "Attack -> hit"); return true; }
            // Otherwise, the attempt did nothing (likely no actions left); keep searching or end turn.
            continue;
        }
        return false;
    }

    // --- Smart advance using BFS and threat map ---
    private boolean trySmartAdvance(Board b) {
        ThreatMap threats = computeThreatMap(b, PieceAlignment.P1);
        int rows = b.getROWS(), cols = b.getCOLS();
        // Collect enemy positions for distance heuristic
        List<int[]> enemies = new ArrayList<>();
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            GamePiece gp = b.getGamePieceAtPos(r,c);
            if (gp instanceof MonsterGamePiece em && em.getAlignment() == PieceAlignment.P1) enemies.add(new int[]{r,c});
        }
        if (enemies.isEmpty()) return false;

        // For each of our units, try to choose a safe reachable tile that reduces BFS distance
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            GamePiece gp = b.getGamePieceAtPos(r,c);
            if (!(gp instanceof MonsterGamePiece me) || me.getAlignment() != PieceAlignment.P2) continue;
            int speed = me.getStats().getSpeed();
            List<Plot> reach = b.getReachablePlots(r,c,speed);
            // measure current min Manhattan to enemy
            int currentDist = nearestManhattan(r,c,enemies);
            Plot best = null; int bestDist = currentDist;
            for (Plot p : reach) {
                int[] idx = b.getIndicesOfPlot(p); if (idx == null) continue;
                int d = nearestManhattan(idx[0], idx[1], enemies);
                if (d < bestDist && !threats.isThreatened(idx[0], idx[1])) {
                    bestDist = d; best = p;
                }
            }
            // If no safe tile reduces distance, consider first-strike adjacent engage
            if (best == null) {
                for (Plot p : reach) {
                    int[] idx = b.getIndicesOfPlot(p); if (idx == null) continue;
                    // If this move would place us adjacent to an enemy we can kill immediately this turn, allow it
                    if (wouldEnableLethal(me, b, idx[0], idx[1])) { best = p; break; }
                }
            }
            if (best != null) {
                Renderable srcR = b.getPlotAtPos(r,c);
                if (srcR instanceof Plot srcPlot) {
                    int[] bestIdx = b.getIndicesOfPlot(best); if (bestIdx == null) continue;
                    GamePiece before = b.getGamePieceAtPos(r,c);
                    HashMap<Integer, CustomBox> entities = new HashMap<>();
                    entities.put(0, srcPlot); entities.put(1, best);
                    srcPlot.triggerClickEffect(entities);
                    GamePiece afterAtDest = b.getGamePieceAtPos(bestIdx[0], bestIdx[1]);
                    if (afterAtDest == before) { Logger.log("[SmartBot]", "Advance to ("+bestIdx[0]+","+bestIdx[1]+")"); return true; }
                }
            }
        }
        return false;
    }

    private boolean wouldEnableLethal(MonsterGamePiece me, Board b, int toR, int toC) {
        // Check if from (toR,toC) we have an adjacent enemy that can be killed with one attack
        List<int[]> adj = List.of(new int[]{toR+1,toC}, new int[]{toR-1,toC}, new int[]{toR,toC+1}, new int[]{toR,toC-1});
        for (int[] a : adj) {
            if (!inBounds(b,a[0],a[1])) continue;
            GamePiece gp = b.getGamePieceAtPos(a[0], a[1]);
            if (gp instanceof MonsterGamePiece em && em.getAlignment() == PieceAlignment.P1) {
                int dmg = me.getStats().getDamage();
                int hp = em.getStats().getCurrentHealth();
                if (dmg >= hp) return true;
            }
        }
        return false;
    }

    private boolean inBounds(Board b, int r, int c) {
        return r >= 0 && r < b.getROWS() && c >= 0 && c < b.getCOLS();
    }

    private int nearestManhattan(int r, int c, List<int[]> enemies) {
        int best = Integer.MAX_VALUE; for (int[] e : enemies) { int d = Math.abs(e[0]-r)+Math.abs(e[1]-c); if (d<best) best=d; } return best;
    }

    // --- Defensive summon using any SummonCard ---
    private boolean tryDefensiveSummon(Board b) {
        var ps = PlayerManager.get(PieceAlignment.P2);
        if (ps == null || ps.hand == null) return false;
        // identify imminent threat columns (closest enemy to our home row)
        int homeRow = b.getROWS() - 1;
        int cols = b.getCOLS();
        int[] bestColAndDist = {-1, Integer.MAX_VALUE};
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < b.getROWS(); r++) {
                GamePiece gp = b.getGamePieceAtPos(r,c);
                if (gp instanceof MonsterGamePiece em && em.getAlignment() == PieceAlignment.P1) {
                    int dist = Math.abs(homeRow - r);
                    if (dist < bestColAndDist[1]) { bestColAndDist[0] = c; bestColAndDist[1] = dist; }
                    break; // nearest in this column is enough
                }
            }
        }
        if (bestColAndDist[0] == -1) return false; // no enemies -> skip summoning for now

        // Choose the best affordable SummonCard (highest cost we can afford)
        SummonCard chosen = null; int bestCost = -1;
        int mana = ps.mana;
        for (Card c : ps.hand.getCards()) {
            if (c instanceof SummonCard sc) {
                int cost = sc.getManaCost();
                if (cost <= mana && cost > bestCost) { chosen = sc; bestCost = cost; }
            }
        }
        if (chosen == null) return false;

        // Try home row plots in the target column first; fallback to any valid home row plot
        List<Plot> candidates = new ArrayList<>();
        int targetCol = bestColAndDist[0];
        Renderable rPlot = b.getPlotAtPos(homeRow, targetCol);
        if (rPlot instanceof Plot p && b.isValidSummonTarget(p, PieceAlignment.P2)) candidates.add(p);
        if (candidates.isEmpty()) {
            for (int c = 0; c < cols; c++) {
                Renderable rp = b.getPlotAtPos(homeRow, c);
                if (rp instanceof Plot pp && b.isValidSummonTarget(pp, PieceAlignment.P2)) candidates.add(pp);
            }
        }
        if (candidates.isEmpty()) return false;

        // Play the card on the first candidate
        int beforeSize = ps.hand.getCards().size(); int beforeMana = ps.mana;
        HashMap<Integer, CustomBox> entities = new HashMap<>();
        entities.put(0, chosen); entities.put(1, candidates.get(0));
        ((SummonCard) chosen).triggerClickEffect(entities);
        boolean consumed = !ps.hand.getCards().contains(chosen) || ps.hand.getCards().size() < beforeSize;
        boolean spentMana = ps.mana < beforeMana;
        if (consumed || spentMana) { Logger.log("[SmartBot]", "Summoned defensively"); return true; }
        return false;
    }

    // --- Threat map ---
    private static class ThreatMap {
        private final boolean[][] threatened;
        ThreatMap(int rows, int cols) { threatened = new boolean[rows][cols]; }
        void mark(int r, int c) { if (r>=0 && c>=0 && r<threatened.length && c<threatened[0].length) threatened[r][c] = true; }
        boolean isThreatened(int r, int c) { return r>=0 && c>=0 && r<threatened.length && c<threatened[0].length && threatened[r][c]; }
    }

    private ThreatMap computeThreatMap(Board b, PieceAlignment enemySide) {
        int rows = b.getROWS(), cols = b.getCOLS();
        ThreatMap map = new ThreatMap(rows, cols);
        // For each enemy, mark tiles within T = (actions-1)*speed + 1 steps (cardinal BFS)
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            GamePiece gp = b.getGamePieceAtPos(r,c);
            if (!(gp instanceof MonsterGamePiece em) || em.getAlignment() != enemySide) continue;
            int actions;
            Object v = em.getData(GamePieceData.ACTIONS_REMAINING);
            if (v instanceof Integer n) actions = n; else actions = em.getStats().getActions();
            if (actions <= 0) continue;
            int speed = em.getStats().getSpeed();
            int T = Math.max(1, (actions - 1) * speed + 1);
            // BFS flood up to T steps through empty or enemy-occupied tiles (cannot pass through any piece), but we just mark reachable empty squares plus the ring for attack
            Queue<int[]> q = new ArrayDeque<>();
            boolean[][] seen = new boolean[rows][cols];
            q.add(new int[]{r,c,0}); seen[r][c] = true;
            while (!q.isEmpty()) {
                int[] cur = q.poll();
                int cr = cur[0], cc = cur[1], d = cur[2];
                if (d > T) continue;
                map.mark(cr, cc);
                if (d == T) continue;
                int[][] dirs = new int[][]{{1,0},{-1,0},{0,1},{0,-1}};
                for (int[] dir : dirs) {
                    int nr = cr + dir[0], nc = cc + dir[1];
                    if (nr<0||nc<0||nr>=rows||nc>=cols||seen[nr][nc]) continue;
                    // cannot traverse through any occupied tile except potentially the origin? Keep simple: block if occupied.
                    if (b.getGamePieceAtPos(nr,nc) != null) continue;
                    seen[nr][nc] = true;
                    q.add(new int[]{nr,nc,d+1});
                }
            }
        }
        return map;
    }
}
