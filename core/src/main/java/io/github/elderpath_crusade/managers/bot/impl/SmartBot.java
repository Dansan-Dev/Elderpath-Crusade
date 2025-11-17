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
    // Directional bias for movement: prefer forward (toward row 0), neutral sideways, discourage backward
    private static final int DIR_FORWARD_BONUS = 3;
    private static final int DIR_BACKWARD_PENALTY = 3;

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

    private final Random rng = new Random(initSeed());

    private static long initSeed() {
        try {
            String prop = System.getProperty("smartBotSeed");
            if (prop == null || prop.isBlank()) prop = System.getenv("SMARTBOT_SEED");
            if (prop != null && !prop.isBlank()) return Long.parseLong(prop.trim());
        } catch (Exception ignored) {}
        return 1337L;
    }

    private boolean executeBestIntent(Board b) {
        List<Intent> intents = new ArrayList<>();
        // Immediate win (single or multi-action this turn direct to row 0) is handled in buildWinIntents/buildWinPathIntents
        buildWinIntents(b, intents);
        buildWinPathIntents(b, intents);
        buildAdjacentAttackIntents(b, intents);
        buildAdvanceIntents(b, intents);
        buildManeuverIntents(b, intents);
        buildSummonIntents(b, intents);
        if (intents.isEmpty()) return false;
        // Determine the best score and tie-priority, then pick deterministically with seeded RNG among equals
        int bestScore = intents.stream().mapToInt(it -> it.score).max().orElse(Integer.MIN_VALUE);
        List<Intent> best = new ArrayList<>();
        for (Intent it : intents) if (it.score == bestScore) best.add(it);
        if (best.isEmpty()) return false;
        int bestPri = best.stream().mapToInt(it -> tiePriority(it.kind)).min().orElse(5);
        List<Intent> finalists = new ArrayList<>();
        for (Intent it : best) if (tiePriority(it.kind) == bestPri) finalists.add(it);
        // Shuffle finalists deterministically by seeded RNG to break ties reproducibly
        for (int i = finalists.size()-1; i > 0; i--) {
            int j = rng.nextInt(i+1);
            Intent tmp = finalists.get(i);
            finalists.set(i, finalists.get(j));
            finalists.set(j, tmp);
        }
        for (Intent it : finalists) {
            try {
                if (it.exec.get()) { Logger.log("[SmartBot]", "Intent executed: " + it.kind + " (score=" + it.score + ")"); return true; }
            } catch (Exception e) { /* try next finalist */ }
        }
        // Fallback: try remaining non-finalists in sorted order
        intents.sort((a,bx) -> {
            int cmp = Integer.compare(bx.score, a.score);
            if (cmp != 0) return cmp;
            return Integer.compare(tiePriority(a.kind), tiePriority(bx.kind));
        });
        for (Intent it : intents) {
            if (finalists.contains(it)) continue;
            try { if (it.exec.get()) { Logger.log("[SmartBot]", "Intent executed(fallback): " + it.kind + " (score=" + it.score + ")"); return true; } }
            catch (Exception ignore) {}
        }
        return false;
    }

    private int tiePriority(String kind) {
        if (kind == null) return 5;
        if ("ADJ_ATTACK".equals(kind)) return 0;
        if (kind.startsWith("WIN_")) return 1;
        if ("ADVANCE".equals(kind)) return 2;
        if ("MANEUVER".equals(kind)) return 3;
        if ("DEF_SUMMON".equals(kind)) return 4;
        return 5;
    }

    private void buildWinIntents(Board b, List<Intent> out) {
        int rows = b.getROWS(), cols = b.getCOLS();
        int targetRow = 0;
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            GamePiece gp = b.getGamePieceAtPos(r,c);
            if (!(gp instanceof MonsterGamePiece me) || me.getAlignment() != PieceAlignment.P2) continue;
            if (getRemainingActions(me) <= 0) continue;
            int speed = me.getEffectiveSpeed();
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

    // Multi-turn (0–2 turns) win path intents; simple model (no attack edges inside search)
    private void buildWinPathIntents(Board b, List<Intent> out) {
        ThreatMap threats = computeThreatMap(b, PieceAlignment.P1);
        int rows = b.getROWS(), cols = b.getCOLS();
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            GamePiece gp = b.getGamePieceAtPos(r,c);
            if (!(gp instanceof MonsterGamePiece me) || me.getAlignment() != PieceAlignment.P2) continue;
            int actionsRem = getRemainingActions(me);
            if (actionsRem <= 0) continue;
            WinPathResult res = estimateTurnsToRow0(b, me, r, c, threats, actionsRem);
            if (res == null || res.turns > 2 || res.firstMove == null) continue;
            int base;
            if (res.turns == 0) base = 100; // win this turn via multi-move
            else if (res.turns == 1) base = 95;
            else base = 88; // 2 turns by user preference
            int penalty = Math.min(20, res.threatExposure * 5);
            final Plot dest = res.firstMove; int[] diTmp = b.getIndicesOfPlot(dest);
            boolean lethalThisTurnEnd = false;
            if (diTmp != null && res.turns > 0) {
                lethalThisTurnEnd = isLethalThreatNextTurn(b, me, diTmp[0], diTmp[1]);
            }
            if (res.endThreatThisTurn && res.turns > 0 && diTmp != null) {
                int count = threats.getCount(diTmp[0], diTmp[1]);
                int extra = Math.min(15, 5 * Math.max(0, count - 1));
                penalty += 15 + extra; // stronger penalty scaled by number of threat sources
            }
            if (lethalThisTurnEnd && res.turns > 0) penalty += 25; // extra strong penalty for lethal exposure
            int score = Math.max(55, base - penalty);
            final int sr = r, sc = c; final GamePiece ref = gp; int[] di = diTmp;
            if (di == null) continue;
            String kind = (res.turns == 0) ? "WIN_PATH0" : (res.turns == 1 ? "WIN_PATH1" : "WIN_PATH2");
            out.add(new Intent(score, () -> moveAndVerify(b, sr, sc, dest, ref, di[0], di[1]), kind));
        }
    }

    private static final int MAX_WIN_EXPANSIONS = 200;

    private static class WinPathResult {
        final int turns; final Plot firstMove; final int threatExposure; final boolean endThreatThisTurn;
        WinPathResult(int turns, Plot firstMove, int threatExposure, boolean endThreatThisTurn) {
            this.turns = turns; this.firstMove = firstMove; this.threatExposure = threatExposure; this.endThreatThisTurn = endThreatThisTurn;
        }
    }

    private WinPathResult estimateTurnsToRow0(Board b, MonsterGamePiece me, int sr, int sc, ThreatMap threats, int actionsThisTurn) {
        int effSpeed = me.getEffectiveSpeed();
        int effActions = me.getEffectiveActions();
        int rows = b.getROWS(), cols = b.getCOLS();
        // Quick check: already on row 0
        if (sr == 0) return new WinPathResult(0, null, 0, false);
        // Cache of reachable plots for a source tile
        Map<Integer, List<int[]>> reachCache = new HashMap<>();
        java.util.function.Function<Integer, List<int[]>> getNeighbors = (key) -> {
            List<int[]> cached = reachCache.get(key);
            if (cached != null) return cached;
            int r = key / 1000, c = key % 1000;
            List<int[]> list = new ArrayList<>();
            List<Plot> plots = b.getReachablePlots(r, c, effSpeed);
            for (Plot p : plots) { int[] idx = b.getIndicesOfPlot(p); if (idx != null) list.add(new int[]{idx[0], idx[1]}); }
            reachCache.put(key, list);
            return list;
        };
        class State { int r,c,t,a,th; int fr=-1,fc=-1; boolean endThreat0=false; }
        Deque<State> q = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        State start = new State(); start.r=sr; start.c=sc; start.t=0; start.a=actionsThisTurn; start.th=0; q.add(start); seen.add(sr+","+sc+",0,"+start.a);
        int expansions = 0;
        while (!q.isEmpty() && expansions < MAX_WIN_EXPANSIONS) {
            State s = q.poll();
            // Goal check
            if (s.r == 0) {
                Plot first = null;
                if (s.fr != -1) {
                    Renderable rp = b.getPlotAtPos(s.fr, s.fc); if (rp instanceof Plot p) first = p;
                }
                return new WinPathResult(s.t, first, s.th, s.endThreat0);
            }
            if (s.t > 2) continue;
            // Option: end turn now (roll to next layer) — once per (r,c,t)
            {
                int newT = s.t + 1;
                if (newT <= 2) {
                    int th = s.th + ((threats != null && threats.isThreatened(s.r, s.c)) ? 1 : 0);
                    State ns = new State(); ns.r=s.r; ns.c=s.c; ns.t=newT; ns.a=effActions; ns.th=th; ns.fr=s.fr; ns.fc=s.fc; ns.endThreat0 = s.endThreat0 || (s.t==0 && threats != null && threats.isThreatened(s.r, s.c));
                    String key = ns.r+","+ns.c+","+ns.t+","+ns.a;
                    if (!seen.contains(key)) { seen.add(key); q.add(ns); }
                }
            }
            // If no actions left, skip move expansions (turn roll handled above)
            if (s.a <= 0) continue;
            // Expand one move: all empty plots reachable from (r,c) within speed
            int key = s.r*1000 + s.c;
            for (int[] nb : getNeighbors.apply(key)) {
                expansions++;
                int nr = nb[0], nc = nb[1];
                State ns = new State(); ns.r=nr; ns.c=nc; ns.t=s.t; ns.a=s.a-1; ns.th=s.th; ns.fr=(s.fr==-1? nr : s.fr); ns.fc=(s.fc==-1? nc : s.fc); ns.endThreat0 = s.endThreat0;
                String k = ns.r+","+ns.c+","+ns.t+","+ns.a;
                if (!seen.contains(k)) { seen.add(k); q.add(ns); }
                if (expansions >= MAX_WIN_EXPANSIONS) break;
            }
        }
        return null; // not found within caps
    }

    private void buildAdjacentAttackIntents(Board b, List<Intent> out) {
        int rows = b.getROWS(), cols = b.getCOLS();
        // Precompute next-turn threat map (with attack rings) from enemies
        ThreatMap threats = computeThreatMap(b, PieceAlignment.P1);
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            GamePiece gp = b.getGamePieceAtPos(r,c);
            if (!(gp instanceof MonsterGamePiece me) || me.getAlignment() != PieceAlignment.P2) continue;
            int actionsBefore = getRemainingActions(me);
            if (actionsBefore <= 0) continue;
            List<Plot> hostile = b.getAttackableEnemyPlots(r,c, PieceAlignment.P2);
            if (hostile == null || hostile.isEmpty()) continue;
            for (Plot dst : hostile) {
                int[] d = b.getIndicesOfPlot(dst); if (d == null) continue;
                GamePiece defender = b.getGamePieceAtPos(d[0], d[1]);
                int base = 70;
                boolean lethal = false;
                if (defender instanceof MonsterGamePiece em) {
                    int dmg = ((MonsterGamePiece) gp).getEffectiveDamage();
                    int hp = em.getStats().getCurrentHealth();
                    lethal = dmg >= hp;
                    if (lethal) base = 85; // lethal preferred (80 +5 bonus per user)
                    base += Math.min(10, em.getStats().getCost()); // small bump for value
                    // Target danger: prefer removing higher-damage enemies
                    base += Math.min(3, Math.max(0, em.getEffectiveDamage()));
                    // Defensive urgency: closer to our home row (ROWS-1) is slightly higher priority
                    int defensiveUrgency = Math.max(0, (b.getROWS()-1) - d[0]);
                    base += Math.min(3, defensiveUrgency / 2);
                }
                // Post-attack survivability: penalize if our current tile is lethal next turn and we likely can't move after attacking
                boolean lethalHere = isLethalThreatNextTurn(b, (MonsterGamePiece) gp, r, c);
                if (lethalHere && actionsBefore <= 1) {
                    base -= 20;
                }
                // Heavy non-lethal threat down-weight: if not one-shot lethal, penalize standing in threatened tile after the attack
                if (!lethalHere && !lethal && threats != null && threats.isThreatened(r, c)) {
                    int tCount = threats.getCount(r, c);
                    int penalty = 8 + Math.min(12, 3 * Math.max(0, tCount - 1));
                    if (actionsBefore <= 1) penalty += 5; // more cautious if we can't step away after
                    base -= penalty;
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
            int speed = me.getEffectiveSpeed();
            List<Plot> reach = b.getReachablePlots(r,c,speed);
            int currentDist = nearestManhattan(r,c,enemies);
            // Detect Rogue free-strike ability once per unit
            boolean hasRogue = false;
            try {
                for (var ab : me.getAbilities()) {
                    if (ab != null && ab.getClass().getSimpleName().equals("RogueFreeStrikeAbility")) { hasRogue = true; break; }
                }
            } catch (Exception ignored) {}
            Plot best = null; int bestDist = currentDist;
            // Rogue special: if moving to any reachable tile yields an immediate lethal free strike, prefer that outright
            if (hasRogue && reach != null && !reach.isEmpty()) {
                Plot lethalDest = null; int[] lethalIdx = null; int bestForwardBias = -9999;
                for (Plot p : reach) {
                    int[] idx = b.getIndicesOfPlot(p); if (idx == null) continue;
                    java.util.List<Plot> atkFromDest = b.getAttackableEnemyPlots(idx[0], idx[1], PieceAlignment.P2);
                    if (atkFromDest == null || atkFromDest.isEmpty()) continue;
                    boolean lethalExists = false;
                    for (Plot tp : atkFromDest) {
                        int[] di = b.getIndicesOfPlot(tp); if (di == null) continue;
                        GamePiece tgp = b.getGamePieceAtPos(di[0], di[1]);
                        if (tgp instanceof MonsterGamePiece em) {
                            if (me.getEffectiveDamage() >= Math.max(0, em.getStats().getCurrentHealth())) { lethalExists = true; break; }
                        }
                    }
                    if (lethalExists) {
                        // prefer forward moves if multiple lethal options
                        int forwardBias = (idx[0] < r ? 1 : (idx[0] > r ? -1 : 0));
                        if (lethalDest == null || forwardBias > bestForwardBias) { lethalDest = p; lethalIdx = idx; bestForwardBias = forwardBias; }
                    }
                }
                if (lethalDest != null) {
                    final int sr=r, sc=c; final Plot dest=lethalDest; final GamePiece ref=gp; int[] bi = lethalIdx;
                    int score = 95; // decisively prefer lethal move+free-strike over maneuvers
                    // small directional seasoning
                    if (bi[0] < sr) score += DIR_FORWARD_BONUS; else if (bi[0] > sr) score -= DIR_BACKWARD_PENALTY;
                    out.add(new Intent(score, () -> moveAndVerify(b, sr, sc, dest, ref, bi[0], bi[1]), "ADVANCE"));
                    continue; // skip generic pathing for this unit
                }
            }
            for (Plot p : reach) {
                int[] idx = b.getIndicesOfPlot(p); if (idx == null) continue;
                int d = nearestManhattan(idx[0], idx[1], enemies);
                boolean threatened = threats.isThreatened(idx[0], idx[1]);
                boolean allowRogueThreat = false;
                if (threatened && hasRogue) {
                    // If Rogue can free-strike from this destination, allow stepping into a threatened tile
                    java.util.List<Plot> atkFromDestCand = b.getAttackableEnemyPlots(idx[0], idx[1], PieceAlignment.P2);
                    allowRogueThreat = atkFromDestCand != null && !atkFromDestCand.isEmpty();
                }
                if (d < bestDist && (!threatened || allowRogueThreat)) { best = p; bestDist = d; }
            }
            if (best == null) {
                for (Plot p : reach) {
                    int[] idx = b.getIndicesOfPlot(p); if (idx == null) continue;
                    // allow if it enables immediate lethal (incl. Rogue free-strike) even if threatened
                    if (wouldEnableLethal(me, b, idx[0], idx[1])) { best = p; bestDist = nearestManhattan(idx[0], idx[1], enemies); break; }
                }
            }
            if (best != null) {
                final int sr=r, sc=c; final Plot dest=best; final GamePiece ref=gp; int[] bi = b.getIndicesOfPlot(best);
                int gain = Math.max(0, currentDist - bestDist);
                int score = 50 + Math.min(10, gain);
                // Directional bias: prefer moving forward (toward row 0) over backward
                if (bi[0] < sr) score += DIR_FORWARD_BONUS; // forward (toward row 0)
                else if (bi[0] > sr) score -= DIR_BACKWARD_PENALTY; // backward (toward our home row)
                // Rogue synergy: if this unit has a free strike after moving and destination yields an attack, boost score
                boolean hasRogueFreeStrike = false;
                if (gp instanceof MonsterGamePiece me2) {
                    // Check by ability simple name to avoid tight coupling
                    try {
                        for (var ab : me2.getAbilities()) {
                            if (ab != null && ab.getClass().getSimpleName().equals("RogueFreeStrikeAbility")) { hasRogueFreeStrike = true; break; }
                        }
                    } catch (Exception ignored) {}
                    if (hasRogueFreeStrike) {
                        // Compute attackables from the destination tile
                        java.util.List<Plot> atkFromDest = b.getAttackableEnemyPlots(bi[0], bi[1], PieceAlignment.P2);
                        if (atkFromDest != null && !atkFromDest.isEmpty()) {
                            int bonus = 28; // baseline bonus for creating an immediate free strike
                            // If any target at dest would be lethal, add larger bonus
                            int dmg = me2.getEffectiveDamage();
                            boolean lethalExists = false;
                            for (Plot p2 : atkFromDest) {
                                int[] di = b.getIndicesOfPlot(p2); if (di == null) continue;
                                GamePiece tgp = b.getGamePieceAtPos(di[0], di[1]);
                                if (tgp instanceof MonsterGamePiece em) {
                                    if (dmg >= Math.max(0, em.getStats().getCurrentHealth())) { lethalExists = true; break; }
                                }
                            }
                            if (lethalExists) bonus += 20;
                            score += bonus;
                        }
                    }
                }
                out.add(new Intent(score, () -> moveAndVerify(b, sr, sc, dest, ref, bi[0], bi[1]), "ADVANCE"));
            }
        }
    }

    // Fallback: safer repositioning or standing still to avoid harm and unblock lanes
    private void buildManeuverIntents(Board b, List<Intent> out) {
        ThreatMap threats = computeThreatMap(b, PieceAlignment.P1);
        int rows = b.getROWS(), cols = b.getCOLS();
        // Precompute enemy positions for distance shaping
        List<int[]> enemies = new ArrayList<>();
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            GamePiece gp = b.getGamePieceAtPos(r,c);
            if (gp instanceof MonsterGamePiece em && em.getAlignment() == PieceAlignment.P1) enemies.add(new int[]{r,c});
        }
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            GamePiece gp = b.getGamePieceAtPos(r,c);
            if (!(gp instanceof MonsterGamePiece me) || me.getAlignment() != PieceAlignment.P2) continue;
            int actionsRem = getRemainingActions(me);
            if (actionsRem <= 0) continue;
            int speed = me.getEffectiveSpeed();
            List<Plot> reach = b.getReachablePlots(r,c,speed);
            // Include staying put as a candidate
            Plot stayPlot = null; Renderable rr = b.getPlotAtPos(r,c); if (rr instanceof Plot sp) stayPlot = sp;
            List<Plot> candidates = new ArrayList<>();
            if (stayPlot != null) candidates.add(stayPlot);
            if (reach != null) candidates.addAll(reach);

            boolean underAdjThreatNow = threats.isThreatened(r, c);
            int bestScore = Integer.MIN_VALUE; Plot bestDest = null; int[] bestIdx = null;

            // Identify closest ally behind in same column for decongestion
            class AllyInfo { MonsterGamePiece ally; int row; int col; int weight; }
            AllyInfo laneAlly = null;
            for (int rrw = r+1; rrw < rows; rrw++) {
                GamePiece g2 = b.getGamePieceAtPos(rrw, c);
                if (g2 instanceof MonsterGamePiece m2) {
                    if (m2.getAlignment() == PieceAlignment.P2) {
                        laneAlly = new AllyInfo(); laneAlly.ally = m2; laneAlly.row = rrw; laneAlly.col = c;
                        // Compute ally weight
                        int w = m2.getStats().getDamage()*2 + m2.getStats().getActions() + m2.getStats().getSpeed();
                        // turns-to-win comparison bonus
                        WinPathResult myTTW = estimateTurnsToRow0(b, me, r, c, threats, actionsRem);
                        WinPathResult allyTTW = estimateTurnsToRow0(b, m2, rrw, c, threats, getRemainingActions(m2));
                        if (myTTW != null && allyTTW != null && allyTTW.turns < (myTTW.turns == 0 ? 0 : myTTW.turns)) w += 6;
                        laneAlly.weight = w;
                        break;
                    } else {
                        // enemy piece in same column blocks; stop scanning
                        break;
                    }
                }
            }

            int currentDist = enemies.isEmpty() ? Integer.MAX_VALUE : nearestManhattan(r,c,enemies);
            WinPathResult myTTWNow = estimateTurnsToRow0(b, me, r, c, threats, actionsRem);

            for (Plot p : candidates) {
                int[] idx = b.getIndicesOfPlot(p); if (idx == null) continue;
                int dr = idx[0], dc = idx[1];
                // Discard one-shot lethal
                if (isLethalThreatNextTurn(b, me, dr, dc)) continue;
                int score = 40;
                // Penalize threatened (non-lethal)
                if (threats.isThreatened(dr, dc)) {
                    int tCount = threats.getCount(dr, dc);
                    int penalty = 20 + Math.min(15, 5 * Math.max(0, tCount - 1));
                    score -= penalty;
                }
                // Directional bias (forward > sideways > backward). Apply after safety check.
                if (dr < r) score += DIR_FORWARD_BONUS; // forward
                else if (dr > r) score -= DIR_BACKWARD_PENALTY; // backward

                // Under immediate adjacency threat at current tile: discourage sidestep unless it fully exits threat and unblocks
                boolean isSidestep = (dc != c) || (dr == r && dc != c);
                if (underAdjThreatNow && !(dr == r && dc == c)) {
                    boolean exitsThreat = !threats.isThreatened(dr, dc);
                    int unblockBonus = 0;
                    if (laneAlly != null && dc != c) {
                        unblockBonus = 8 + Math.min(7, laneAlly.weight / 4);
                    }
                    if (!(exitsThreat && unblockBonus >= 10)) {
                        score -= 12; // prefer standing still
                    } else {
                        score += unblockBonus;
                    }
                }

                // Decongestion scoring when not in immediate-adj case
                if (!underAdjThreatNow) {
                    if (laneAlly != null) {
                        if (dc == c) {
                            // staying in lane ahead of ally → blocking penalty
                            int blockPenalty = 6 + Math.min(10, laneAlly.weight / 3);
                            // If we move further up (smaller row index), still blocking; if we move off-lane later, that's in other branch
                            score -= blockPenalty;
                        } else {
                            // Move off lane to free ally
                            int bonus = 8 + Math.min(7, laneAlly.weight / 4);
                            score += bonus;
                        }
                    }
                }

                // Future opportunity: win path improvement relative to staying (account for action spend)
                int actionsAfter = actionsRem - ((dr == r && dc == c) ? 0 : 1);
                if (actionsAfter < 0) actionsAfter = 0;
                WinPathResult myTTWDest = estimateTurnsToRow0(b, me, dr, dc, threats, actionsAfter);
                if (myTTWNow != null && myTTWDest != null) {
                    int from = myTTWNow.turns; int to = myTTWDest.turns;
                    if (from > to) {
                        if ((from == 2 && to == 1)) score += 6;
                        else if (to == 0) score += 10;
                        else score += 4; // small improvement otherwise
                    }
                }

                // Enemy engagement shaping (only if dest not threatened)
                if (!threats.isThreatened(dr, dc) && !enemies.isEmpty()) {
                    int newDist = nearestManhattan(dr, dc, enemies);
                    if (newDist < currentDist) score += Math.min(6, currentDist - newDist);
                }

                if (score > bestScore) { bestScore = score; bestDest = p; bestIdx = idx; }
            }

            // Only emit if bestDest is a different tile (real action); otherwise we implicitly stand still
            if (bestDest != null && !(bestIdx[0] == r && bestIdx[1] == c)) {
                final int sr=r, sc=c; final Plot dest=bestDest; final GamePiece ref=gp; final int dr=bestIdx[0], dc=bestIdx[1];
                int finalScore = bestScore;
                out.add(new Intent(finalScore, () -> moveAndVerify(b, sr, sc, dest, ref, dr, dc), "MANEUVER"));
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
                List<Plot> hostile = b.getAttackableEnemyPlots(r, c, PieceAlignment.P2);
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
        return mgp.getStats().getRemainingActions();
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
            List<Plot> hostile = b.getAttackableEnemyPlots(r,c, PieceAlignment.P2);
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
                    // but avoid stepping into a tile that is lethal next turn for us
                    if (wouldEnableLethal(me, b, idx[0], idx[1]) && !isLethalThreatNextTurn(b, me, idx[0], idx[1])) { best = p; break; }
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
                if (me.getEffectiveDamage() >= hp) return true;
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
        private final int[][] threatCount;
        ThreatMap(int rows, int cols) { threatCount = new int[rows][cols]; }
        void mark(int r, int c) { if (r>=0 && c>=0 && r<threatCount.length && c<threatCount[0].length) threatCount[r][c]++; }
        boolean isThreatened(int r, int c) { return r>=0 && c>=0 && r<threatCount.length && c<threatCount[0].length && threatCount[r][c] > 0; }
        int getCount(int r, int c) { return (r>=0 && c>=0 && r<threatCount.length && c<threatCount[0].length) ? threatCount[r][c] : 0; }
    }

    private ThreatMap computeThreatMap(Board b, PieceAlignment enemySide) {
        int rows = b.getROWS(), cols = b.getCOLS();
        ThreatMap map = new ThreatMap(rows, cols);
        // For each enemy with non-zero attack, mark tiles they can attack NEXT TURN
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            GamePiece gp = b.getGamePieceAtPos(r,c);
            if (!(gp instanceof MonsterGamePiece em) || em.getAlignment() != enemySide) continue;
            // Skip enemies that cannot deal damage
            if (em.getEffectiveDamage() <= 0) continue;
            int actions = em.getEffectiveActions();
            if (actions <= 0) continue;
            int speed = em.getEffectiveSpeed();
            // Movement steps available before an attack next turn
            int moveStepsForAttack = Math.max(0, (actions - 1) * Math.max(0, speed));
            // BFS flood up to moveStepsForAttack steps through empty tiles (cannot pass through any piece)
            Queue<int[]> q = new ArrayDeque<>();
            boolean[][] seen = new boolean[rows][cols];
            q.add(new int[]{r,c,0}); seen[r][c] = true;
            final int[][] dirs = new int[][]{{1,0},{-1,0},{0,1},{0,-1}};
            while (!q.isEmpty()) {
                int[] cur = q.poll();
                int cr = cur[0], cc = cur[1], d = cur[2];
                if (d > moveStepsForAttack) continue;
                // From any tile we can occupy after moving 'd' steps (including start), we can attack its 4-adjacent tiles
                for (int[] dir : dirs) {
                    int ar = cr + dir[0], ac = cc + dir[1];
                    map.mark(ar, ac);
                }
                if (d == moveStepsForAttack) continue;
                // Continue flood through empty tiles only
                for (int[] dir : dirs) {
                    int nr = cr + dir[0], nc = cc + dir[1];
                    if (nr<0||nc<0||nr>=rows||nc>=cols||seen[nr][nc]) continue;
                    // cannot traverse through occupied tiles
                    if (b.getGamePieceAtPos(nr,nc) != null) continue;
                    seen[nr][nc] = true;
                    q.add(new int[]{nr,nc,d+1});
                }
            }
        }
        return map;
    }

    // Check if tile (r,c) would be one-shot lethal for 'me' on the enemy's next turn
    private boolean isLethalThreatNextTurn(Board b, MonsterGamePiece me, int r, int c) {
        int rows = b.getROWS(), cols = b.getCOLS();
        int myHp = Math.max(0, me.getStats().getCurrentHealth());
        PieceAlignment enemySide = (me.getAlignment() == PieceAlignment.P1) ? PieceAlignment.P2 : PieceAlignment.P1;
        for (int er = 0; er < rows; er++) for (int ec = 0; ec < cols; ec++) {
            GamePiece gp = b.getGamePieceAtPos(er, ec);
            if (!(gp instanceof MonsterGamePiece em) || em.getAlignment() != enemySide) continue;
            int dmg = em.getEffectiveDamage();
            if (dmg <= 0) continue; // non-attacking pieces pose no lethal threat
            int actions = em.getEffectiveActions();
            if (actions <= 0) continue;
            int speed = em.getStats().getSpeed();
            int T = Math.max(1, (actions - 1) * speed + 1);
            // BFS to enumerate end positions the enemy can occupy by end of its next turn
            Queue<int[]> q = new ArrayDeque<>();
            boolean[][] seen = new boolean[rows][cols];
            q.add(new int[]{er, ec, 0}); seen[er][ec] = true;
            while (!q.isEmpty()) {
                int[] cur = q.poll();
                int cr = cur[0], cc = cur[1], d = cur[2];
                if (d > T) continue;
                // If from (cr,cc) the enemy can attack (r,c) next turn (adjacent), check lethal
                if (Math.abs(cr - r) + Math.abs(cc - c) == 1) {
                    if (dmg >= myHp) return true;
                }
                if (d == T) continue;
                int[][] dirs = new int[][]{{1,0},{-1,0},{0,1},{0,-1}};
                for (int[] dir : dirs) {
                    int nr = cr + dir[0], nc = cc + dir[1];
                    if (nr<0||nc<0||nr>=rows||nc>=cols||seen[nr][nc]) continue;
                    if (b.getGamePieceAtPos(nr, nc) != null) continue; // cannot pass through pieces
                    seen[nr][nc] = true;
                    q.add(new int[]{nr, nc, d+1});
                }
            }
        }
        return false;
    }
}
