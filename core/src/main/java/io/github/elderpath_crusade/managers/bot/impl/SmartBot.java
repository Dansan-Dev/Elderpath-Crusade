package io.github.elderpath_crusade.managers.bot.impl;

import com.badlogic.gdx.utils.Timer;
import io.github.elderpath_crusade.enums.PieceAlignment;
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
import io.github.elderpath_crusade.managers.bot.search.*;

import java.util.*;

import java.util.function.Supplier;

/**
 * Smarter bot implementing a simple urgency-driven policy with safeguards:
 * - Win-opportunity: If any unit can reach the opponent home row (row 0) this
 * turn, do it first.
 * - Adjacent attacks.
 * - BFS-guided movement toward closest enemy while avoiding threatened tiles
 * unless first-strike lethal.
 * - Defensive summoning: consider all SummonCards; prioritize blocking columns
 * where enemies are close to our home row.
 *
 * Notes about coordinates: row 0 is bottom, (ROWS-1) is top. P2 home row =
 * ROWS-1, P1 home row = 0.
 */
public class SmartBot implements Bot {
    private static final float STEP_DELAY = 0.35f;
    private static final float END_DELAY = 0.4f;
    private static final int MAX_STEPS = 60;
    // Directional bias for movement: prefer forward (toward row 0), neutral
    // sideways, discourage backward
    private static final int DIR_FORWARD_BONUS = 3;
    private static final int DIR_BACKWARD_PENALTY = 3;

    // Base scores for different intent types
    private static final int SCORE_WIN_NOW = 100;
    private static final int SCORE_WIN_PATH1 = 95;
    private static final int SCORE_WIN_PATH2 = 88;
    private static final int SCORE_ADJ_ATTACK_BASE = 70;
    private static final int SCORE_ADJ_ATTACK_LETHAL = 85;
    private static final int SCORE_ROGUE_LETHAL_MOVE = 95;
    private static final int SCORE_ADVANCE_BASE = 50;
    private static final int SCORE_MANEUVER_BASE = 40;
    private static final int SCORE_SUMMON_BASE = 45;

    // Penalties and modifiers
    private static final int PENALTY_LETHAL_EXPOSURE = 25;
    private static final int PENALTY_THREAT_BASE = 20;
    private static final int PENALTY_WIN_PATH_EXPOSURE_SCALE = 5;
    private static final int PENALTY_WIN_PATH_EXPOSURE_MAX = 20;
    private static final int PENALTY_WIN_PATH_END_THREAT_BASE = 15;
    private static final int PENALTY_WIN_PATH_END_THREAT_EXTRA_SCALE = 5;
    private static final int PENALTY_WIN_PATH_END_THREAT_EXTRA_MAX = 15;
    private static final int SCORE_WIN_PATH_MIN = 55;
    private static final int BONUS_ROGUE_FREE_STRIKE = 28;
    private static final int BONUS_ROGUE_LETHAL = 20;

    // Per-turn state guards to avoid runaway loops after finishing
    private boolean turnActive = false;
    private boolean ended = false;

    @Override
    public String getName() {
        return "SmartBot";
    }

    @Override
    public void onTurnStarted(PieceAlignment player) {
        if (player != PieceAlignment.P2)
            return;
        this.turnActive = true;
        this.ended = false;
        Logger.log("[SmartBot]", "Turn start");
        step(0);
    }

    private void step(int stepsDone) {
        if (!turnActive || ended)
            return;
        if (GraphicsManager.isPaused() || TurnManager.getCurrentPlayer() != PieceAlignment.P2)
            return;
        if (stepsDone >= MAX_STEPS) {
            Logger.log("[SmartBot]", "Reached step cap");
            endTurn();
            return;
        }
        Board board = BoardManager.getBoard();
        if (board == null) {
            endTurn();
            return;
        }

        Logger.log("[SmartBot]", "Step tracker: " + stepsDone);
        if (shouldEndNow(board)) {
            Logger.log("[SmartBot]", "Nothing left to do; ending turn");
            endTurn();
            return;
        }

        if (executeBestIntent(board)) {
            scheduleNext(stepsDone + 1);
            return;
        }

        Logger.log("[SmartBot]", "No more actions; ending turn");
        endTurn();
    }

    private void scheduleNext(final int stepsDone) {
        if (!turnActive || ended)
            return;
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                step(stepsDone);
            }
        }, STEP_DELAY);
    }

    private void endTurn() {
        if (ended)
            return;
        ended = true;
        turnActive = false;
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                if (!GraphicsManager.isPaused())
                    TurnManager.endTurn();
            }
        }, END_DELAY);
    }

    // --- Intent engine ---
    private enum IntentType {
        ADJ_ATTACK,
        WIN_MOVE, WIN_PATH1, WIN_PATH2,
        ADVANCE,
        MANEUVER,
        DEF_SUMMON
    }

    private record Intent(int score, Supplier<Boolean> exec, IntentType kind) {
    }

    private final Random rng = new Random(initSeed());

    private static long initSeed() {
        try {
            String prop = System.getProperty("smartBotSeed");
            if (prop == null || prop.isBlank())
                prop = System.getenv("SMARTBOT_SEED");
            if (prop != null && !prop.isBlank())
                return Long.parseLong(prop.trim());
        } catch (Exception ignored) {
        }
        return 1337L;
    }

    private record PieceEntry(Coord pos, MonsterGamePiece piece) {
    }

    private record TacticalState(List<PieceEntry> allies, List<PieceEntry> enemies, ThreatMap threats) {
    }

    private int nearestManhattan(int r, int c, List<PieceEntry> targets) {
        int min = Integer.MAX_VALUE;
        for (PieceEntry ent : targets) {
            int d = Math.abs(r - ent.pos.row()) + Math.abs(c - ent.pos.col());
            if (d < min)
                min = d;
        }
        return min;
    }

    private boolean executeBestIntent(Board board) {
        if (!turnActive || ended)
            return false;
        List<Intent> intents = new ArrayList<>();

        TacticalState tactical = precomputeTacticalState(board);

        buildWinPathIntents(board, tactical, intents);
        buildAdjacentAttackIntents(board, tactical, intents);
        buildAdvanceIntents(board, tactical, intents);
        buildManeuverIntents(board, tactical, intents);
        buildSummonIntents(board, tactical, intents);

        if (intents.isEmpty())
            return false;

        Collections.shuffle(intents, rng);

        Comparator<Intent> comp = Comparator
                .comparingInt((Intent i) -> i.score).reversed()
                .thenComparingInt(i -> tiePriority(i.kind));
        intents.sort(comp);

        for (Intent intent : intents) {
            try {
                if (intent.exec.get()) {
                    Logger.log("[SmartBot]", "Intent executed: " + intent.kind + " (score=" + intent.score + ")");
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private int tiePriority(IntentType kind) {
        if (kind == null)
            return 5;
        return switch (kind) {
            case ADJ_ATTACK -> 0;
            case WIN_MOVE, WIN_PATH1, WIN_PATH2 -> 1;
            case ADVANCE -> 2;
            case MANEUVER -> 3;
            case DEF_SUMMON -> 4;
        };
    }

    private TacticalState precomputeTacticalState(Board b) {
        List<PieceEntry> allies = new ArrayList<>();
        List<PieceEntry> enemies = new ArrayList<>();
        for (int r = 0; r < b.getROWS(); r++) {
            for (int c = 0; c < b.getCOLS(); c++) {
                GamePiece gp = b.getGamePieceAtPos(r, c);
                if (gp instanceof MonsterGamePiece mgp) {
                    PieceEntry entry = new PieceEntry(new Coord(r, c), mgp);
                    if (mgp.getAlignment() == PieceAlignment.P2)
                        allies.add(entry);
                    else
                        enemies.add(entry);
                }
            }
        }
        ThreatMap threats = computeThreatMap(b, PieceAlignment.P1);
        return new TacticalState(allies, enemies, threats);
    }

    private void buildWinPathIntents(Board b, TacticalState tactical, List<Intent> out) {
        for (PieceEntry entry : tactical.allies) {
            MonsterGamePiece me = entry.piece;
            Coord pos = entry.pos;
            if (!canAct(me))
                continue;

            WinPathResult res = estimateTurnsToRow0(b, me, pos.row(), pos.col(), tactical.threats,
                    getRemainingActions(me));
            if (res == null || res.turns > 2 || res.firstMove == null)
                continue;

            int base = switch (res.turns) {
                case 0 -> SCORE_WIN_NOW;
                case 1 -> SCORE_WIN_PATH1;
                default -> SCORE_WIN_PATH2;
            };

            int penalty = calculateWinPathPenalty(b, me, res, tactical.threats);
            int score = Math.max(SCORE_WIN_PATH_MIN, base - penalty);

            final Coord src = pos;
            final Plot dest = res.firstMove;
            final GamePiece ref = me;
            int[] di = dest.getIndices();
            if (di == null)
                continue;

            IntentType kind = (res.turns == 0) ? IntentType.WIN_MOVE
                    : (res.turns == 1 ? IntentType.WIN_PATH1 : IntentType.WIN_PATH2);
            out.add(new Intent(score, () -> moveAndVerify(b, src.row(), src.col(), dest, ref, di[0], di[1]), kind));
        }
    }

    private int calculateWinPathPenalty(Board b, MonsterGamePiece me, WinPathResult res, ThreatMap threats) {
        int penalty = Math.min(PENALTY_WIN_PATH_EXPOSURE_MAX, res.threatExposure * PENALTY_WIN_PATH_EXPOSURE_SCALE);
        if (res.turns == 0)
            return penalty; // Immediate win ignores end-turn placement threats

        int[] di = res.firstMove.getIndices();
        if (di == null)
            return penalty;

        // Penalty for ending turn in a lethal zone
        if (isLethalThreatNextTurn(b, me, di[0], di[1])) {
            penalty += PENALTY_LETHAL_EXPOSURE;
        }

        // Penalty for ending turn in a non-lethal threat zone
        if (res.endsTurn0InThreat) {
            int attackerCount = threats.getCount(di[0], di[1]);
            int extra = Math.min(PENALTY_WIN_PATH_END_THREAT_EXTRA_MAX,
                    PENALTY_WIN_PATH_END_THREAT_EXTRA_SCALE * Math.max(0, attackerCount - 1));
            penalty += PENALTY_WIN_PATH_END_THREAT_BASE + extra;
        }

        return penalty;
    }

    private static final int MAX_WIN_EXPANSIONS = 200;

    private record WinPathResult(int turns, Plot firstMove, int threatExposure, boolean endsTurn0InThreat) {
    }

    private WinPathResult estimateTurnsToRow0(
            Board board,
            MonsterGamePiece mgp,
            int srcRow, int srcCol,
            ThreatMap threats,
            int actionsThisTurn) {
        int effectiveSpeed = mgp.getEffectiveSpeed();
        int effectiveActions = mgp.getEffectiveActions();

        Map<Coord, List<Coord>> reachCache = new HashMap<>();

        Deque<BotSearchState> queue = new ArrayDeque<>();
        Set<Integer> seen = new HashSet<>();

        Coord startPos = new Coord(srcRow, srcCol);
        BotSearchState start = new BotSearchState(startPos, 0, actionsThisTurn, 0, null, false);
        queue.add(start);
        seen.add(start.pack());

        int expansions = 0;
        while (!queue.isEmpty() && expansions < MAX_WIN_EXPANSIONS) {
            BotSearchState state = queue.poll();
            if (state.pos.row() == 0) {
                Plot first = null;
                if (state.firstMove != null) {
                    Renderable rp = board.getPlotAtPos(state.firstMove.row(), state.firstMove.col());
                    if (rp instanceof Plot p)
                        first = p;
                }
                return new WinPathResult(state.turnDepth, first, state.threatCount, state.endsFirstTurnInThreat);
            }
            if (state.turnDepth > 2)
                continue;
            // 1. Simulate ending turn (waiting for next layer)
            processTurnRollover(state, effectiveActions, threats, seen, queue);

            // 2. Expand move steps (if actions remain)
            if (state.actionsLeft > 0) {
                expansions = exploreMovementOptions(board, state, effectiveSpeed, reachCache, seen, queue, expansions);
            }
        }
        return null; // not found within caps
    }

    private void processTurnRollover(BotSearchState state, int maxActions, ThreatMap threats, Set<Integer> seen,
            Deque<BotSearchState> queue) {
        if (state.turnDepth >= 2)
            return;

        BotSearchState nextTurn = state.waitTurn(threats, maxActions);
        if (seen.add(nextTurn.pack())) {
            queue.add(nextTurn);
        }
    }

    private int exploreMovementOptions(Board board, BotSearchState state, int speed, Map<Coord, List<Coord>> cache,
            Set<Integer> seen, Deque<BotSearchState> queue, int currentExpansions) {
        int expansions = currentExpansions;
        List<Coord> neighbors = cache.computeIfAbsent(state.pos, pos -> {
            List<Coord> list = new ArrayList<>();
            for (Plot p : board.getReachablePlots(pos.row(), pos.col(), speed)) {
                int[] idx = p.getIndices();
                if (idx != null)
                    list.add(new Coord(idx[0], idx[1]));
            }
            return list;
        });

        for (Coord next : neighbors) {
            expansions++;
            BotSearchState nextStep = state.stepTo(next);

            if (seen.add(nextStep.pack())) {
                queue.add(nextStep);
            }
            if (expansions >= MAX_WIN_EXPANSIONS)
                break;
        }
        return expansions;
    }

    private void buildAdjacentAttackIntents(Board board, TacticalState tactical, List<Intent> out) {
        for (PieceEntry entry : tactical.allies) {
            MonsterGamePiece attacker = entry.piece;
            Coord src = entry.pos;
            if (!canAct(attacker))
                continue;

            List<Plot> hostile = board.getAttackableEnemyPlots(src.row(), src.col(), PieceAlignment.P2);
            if (hostile == null)
                continue;

            for (Plot dstPlot : hostile) {
                Coord target = new Coord(dstPlot.getRow(), dstPlot.getCol());
                GamePiece defender = board.getGamePieceAtPos(target.row(), target.col());

                int score = scoreAdjacentAttack(board, src, target, attacker, defender, tactical.threats);
                out.add(new Intent(score,
                        () -> attackAndVerify(board, src, target, attacker),
                        IntentType.ADJ_ATTACK));
            }
        }
    }

    private int scoreAdjacentAttack(Board board, Coord src, Coord target, MonsterGamePiece attacker,
            GamePiece defender, ThreatMap threats) {
        int basePoints = SCORE_ADJ_ATTACK_BASE;
        boolean lethal = false;

        if (defender instanceof MonsterGamePiece mgpDefender) {
            int dmg = attacker.getEffectiveDamage();
            int hp = mgpDefender.getStats().getCurrentHealth();
            lethal = dmg >= hp;

            if (lethal)
                basePoints = SCORE_ADJ_ATTACK_LETHAL;

            basePoints += Math.min(10, mgpDefender.getStats().getCost());
            basePoints += Math.min(3, Math.max(0, mgpDefender.getEffectiveDamage()));

            // Defensive Urgency: prioritize enemies closer to our home row (ROWS-1)
            int rows = board.getROWS();
            int homeRow = rows - 1;
            int distToHome = Math.abs(homeRow - target.row());
            int urgency = Math.max(0, rows - distToHome);
            basePoints += Math.min(5, urgency / 2);
        }

        int actionsBefore = getRemainingActions(attacker);
        // Post-attack survivability
        boolean lethalHere = isLethalThreatNextTurn(board, attacker, src.row(), src.col());
        if (lethalHere && actionsBefore <= 1) {
            basePoints -= PENALTY_THREAT_BASE;
        }

        if (!lethalHere && !lethal && threats.isThreatened(src.row(), src.col())) {
            int tCount = threats.getCount(src.row(), src.col());
            // Value at Risk: Higher value units are more cautious
            int myValueFactor = Math.min(10, attacker.getStats().getCost()) / 3;
            int penalty = 8 + Math.min(12, (3 + myValueFactor) * Math.max(0, tCount - 1));
            if (actionsBefore <= 1)
                penalty += 5;
            basePoints -= penalty;
        }

        return basePoints;
    }

    private void buildAdvanceIntents(Board b, TacticalState tactical, List<Intent> out) {
        if (tactical.enemies.isEmpty())
            return;
        for (PieceEntry entry : tactical.allies) {
            MonsterGamePiece me = entry.piece;
            Coord pos = entry.pos;
            if (!canAct(me))
                continue;
            int speed = me.getEffectiveSpeed();
            List<Plot> reach = b.getReachablePlots(pos.row(), pos.col(), speed);
            int currentDist = nearestManhattan(pos.row(), pos.col(), tactical.enemies);
            // Detect Rogue free-strike ability once per unit
            boolean hasRogue = isRogue(me);
            Plot best = null;
            int bestDist = currentDist;
            // Rogue special: if moving to any reachable tile yields an immediate lethal
            // free strike, prefer that outright
            if (hasRogue && reach != null && !reach.isEmpty()) {
                Plot lethalDest = null;
                int[] lethalIdx = null;
                int bestForwardBias = -9999;
                for (Plot p : reach) {
                    int[] idx = p.getIndices();
                    if (idx == null)
                        continue;
                    java.util.List<Plot> atkFromDest = b.getAttackableEnemyPlots(idx[0], idx[1], PieceAlignment.P2);
                    if (atkFromDest == null || atkFromDest.isEmpty())
                        continue;
                    boolean lethalExists = false;
                    for (Plot tp : atkFromDest) {
                        int[] di = tp.getIndices();
                        if (di == null)
                            continue;
                        GamePiece tgp = b.getGamePieceAtPos(di[0], di[1]);
                        if (tgp instanceof MonsterGamePiece em) {
                            if (me.getEffectiveDamage() >= Math.max(0, em.getStats().getCurrentHealth())) {
                                lethalExists = true;
                                break;
                            }
                        }
                    }
                    if (lethalExists) {
                        // prefer forward moves if multiple lethal options
                        int forwardBias = (idx[0] < pos.row() ? 1 : (idx[0] > pos.row() ? -1 : 0));
                        if (lethalDest == null || forwardBias > bestForwardBias) {
                            lethalDest = p;
                            lethalIdx = idx;
                            bestForwardBias = forwardBias;
                        }
                    }
                }
                if (lethalDest != null) {
                    final Coord src = pos;
                    final Plot dest = lethalDest;
                    final GamePiece ref = me;
                    int[] bi = lethalIdx;
                    int score = SCORE_ROGUE_LETHAL_MOVE; // decisively prefer lethal move+free-strike over maneuvers
                    // small directional seasoning
                    if (bi[0] < src.row())
                        score += DIR_FORWARD_BONUS;
                    else if (bi[0] > src.row())
                        score -= DIR_BACKWARD_PENALTY;
                    out.add(new Intent(score, () -> moveAndVerify(b, src.row(), src.col(), dest, ref, bi[0], bi[1]),
                            IntentType.ADVANCE));
                    continue; // skip generic pathing for this unit
                }
            }
            for (Plot p : reach) {
                int[] idx = p.getIndices();
                if (idx == null)
                    continue;
                int d = nearestManhattan(idx[0], idx[1], tactical.enemies);
                boolean threatened = tactical.threats.isThreatened(idx[0], idx[1]);
                boolean allowRogueThreat = false;
                if (threatened && hasRogue) {
                    // If Rogue can free-strike from this destination, allow stepping into a
                    // threatened tile
                    java.util.List<Plot> atkFromDestCand = b.getAttackableEnemyPlots(idx[0], idx[1],
                            PieceAlignment.P2);
                    allowRogueThreat = atkFromDestCand != null && !atkFromDestCand.isEmpty();
                }
                if (d < bestDist && (!threatened || allowRogueThreat)) {
                    best = p;
                    bestDist = d;
                }
            }
            if (best == null) {
                for (Plot p : reach) {
                    int[] idx = p.getIndices();
                    if (idx == null)
                        continue;
                    // allow if it enables immediate lethal (incl. Rogue free-strike) even if
                    // threatened
                    if (wouldEnableLethal(me, b, idx[0], idx[1])) {
                        best = p;
                        bestDist = nearestManhattan(idx[0], idx[1], tactical.enemies);
                        break;
                    }
                }
            }
            if (best != null) {
                final Coord src = pos;
                final Plot dest = best;
                final GamePiece ref = me;
                int[] bi = best.getIndices();
                int gain = Math.max(0, currentDist - bestDist);
                int score = SCORE_ADVANCE_BASE + Math.min(10, gain);
                // Directional bias: prefer moving forward (toward row 0) over backward
                if (bi[0] < src.row())
                    score += DIR_FORWARD_BONUS; // forward (toward row 0)
                else if (bi[0] > src.row())
                    score -= DIR_BACKWARD_PENALTY; // backward (toward our home row)
                // Rogue synergy: if this unit has a free strike after moving and destination
                // yields an attack, boost score
                boolean hasRogueFreeStrike = isRogue(me);
                if (hasRogueFreeStrike) {
                    // Compute attackables from the destination tile
                    java.util.List<Plot> atkFromDest = b.getAttackableEnemyPlots(bi[0], bi[1],
                            PieceAlignment.P2);
                    if (atkFromDest != null && !atkFromDest.isEmpty()) {
                        int bonus = BONUS_ROGUE_FREE_STRIKE; // baseline bonus for creating an immediate free strike
                        // If any target at dest would be lethal, add larger bonus
                        int dmg = me.getEffectiveDamage();
                        boolean lethalExists = false;
                        for (Plot p2 : atkFromDest) {
                            int[] di = p2.getIndices();
                            if (di == null)
                                continue;
                            GamePiece tgp = b.getGamePieceAtPos(di[0], di[1]);
                            if (tgp instanceof MonsterGamePiece em) {
                                if (dmg >= Math.max(0, em.getStats().getCurrentHealth())) {
                                    lethalExists = true;
                                    break;
                                }
                            }
                        }
                        if (lethalExists)
                            bonus += BONUS_ROGUE_LETHAL;
                        score += bonus;
                    }
                }
                out.add(new Intent(score, () -> moveAndVerify(b, src.row(), src.col(), dest, ref, bi[0], bi[1]),
                        IntentType.ADVANCE));
            }
        }
    }

    // Fallback: safer repositioning or standing still to avoid harm and unblock
    // lanes
    private void buildManeuverIntents(Board b, TacticalState tactical, List<Intent> out) {
        int rows = b.getROWS();
        for (PieceEntry entry : tactical.allies) {
            MonsterGamePiece me = entry.piece;
            Coord pos = entry.pos;
            if (!canAct(me))
                continue;
            int actionsRem = getRemainingActions(me);
            int speed = me.getEffectiveSpeed();
            List<Plot> reach = b.getReachablePlots(pos.row(), pos.col(), speed);
            // Include staying put as a candidate
            Plot stayPlot = null;
            Renderable rr = b.getPlotAtPos(pos.row(), pos.col());
            if (rr instanceof Plot sp)
                stayPlot = sp;
            List<Plot> candidates = new ArrayList<>();
            if (stayPlot != null)
                candidates.add(stayPlot);
            if (reach != null)
                candidates.addAll(reach);

            boolean underAdjThreatNow = tactical.threats.isThreatened(pos.row(), pos.col());
            int bestScore = Integer.MIN_VALUE;
            Plot bestDest = null;
            int[] bestIdx = null;

            // Identify closest ally behind in same column for decongestion
            class AllyInfo {
                int weight;
            }
            AllyInfo laneAlly = null;
            for (int rrw = pos.row() + 1; rrw < rows; rrw++) {
                GamePiece g2 = b.getGamePieceAtPos(rrw, pos.col());
                if (g2 instanceof MonsterGamePiece m2) {
                    if (m2.getAlignment() == PieceAlignment.P2) {
                        laneAlly = new AllyInfo();
                        // Compute ally weight
                        int w = m2.getStats().getDamage() * 2 + m2.getStats().getActions()
                                + m2.getStats().getSpeed();
                        // turns-to-win comparison bonus
                        WinPathResult myTTW = estimateTurnsToRow0(b, me, pos.row(), pos.col(), tactical.threats,
                                actionsRem);
                        WinPathResult allyTTW = estimateTurnsToRow0(b, m2, rrw, pos.col(), tactical.threats,
                                getRemainingActions(m2));
                        if (myTTW != null && allyTTW != null
                                && allyTTW.turns < (myTTW.turns == 0 ? 0 : myTTW.turns))
                            w += 6;
                        laneAlly.weight = w;
                        break;
                    } else {
                        // enemy piece in same column blocks; stop scanning
                        break;
                    }
                }
            }

            int currentDist = tactical.enemies.isEmpty() ? Integer.MAX_VALUE
                    : nearestManhattan(pos.row(), pos.col(), tactical.enemies);
            WinPathResult myTTWNow = estimateTurnsToRow0(b, me, pos.row(), pos.col(), tactical.threats, actionsRem);

            for (Plot p : candidates) {
                int[] idx = p.getIndices();
                if (idx == null)
                    continue;
                int dr = idx[0], dc = idx[1];
                // Discard one-shot lethal
                if (isLethalThreatNextTurn(b, me, dr, dc))
                    continue;
                int score = SCORE_MANEUVER_BASE;
                // Penalize threatened (non-lethal)
                if (tactical.threats.isThreatened(dr, dc)) {
                    int tCount = tactical.threats.getCount(dr, dc);
                    int penalty = PENALTY_THREAT_BASE + Math.min(15, 5 * Math.max(0, tCount - 1));
                    score -= penalty;
                }
                // Directional bias (forward > sideways > backward). Apply after safety check.
                if (dr < pos.row())
                    score += DIR_FORWARD_BONUS; // forward
                else if (dr > pos.row())
                    score -= DIR_BACKWARD_PENALTY; // backward

                // Under immediate adjacency threat at current tile: discourage sidestep unless
                // it fully exits threat and unblocks
                if (underAdjThreatNow && !(dr == pos.row() && dc == pos.col())) {
                    boolean exitsThreat = !tactical.threats.isThreatened(dr, dc);
                    int unblockBonus = 0;
                    if (laneAlly != null && dc != pos.col()) {
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
                        if (dc == pos.col()) {
                            // staying in lane ahead of ally → blocking penalty
                            int blockPenalty = 6 + Math.min(10, laneAlly.weight / 3);
                            // If we move further up (smaller row index), still blocking; if we move
                            // off-lane later, that's in other branch
                            score -= blockPenalty;
                        } else {
                            // Move off lane to free ally
                            int bonus = 8 + Math.min(7, laneAlly.weight / 4);
                            score += bonus;
                        }
                    }
                }

                // Future opportunity: win path improvement relative to staying (account for
                // action spend)
                int actionsAfter = actionsRem - ((dr == pos.row() && dc == pos.col()) ? 0 : 1);
                if (actionsAfter < 0)
                    actionsAfter = 0;
                WinPathResult myTTWDest = estimateTurnsToRow0(b, me, dr, dc, tactical.threats, actionsAfter);
                if (myTTWNow != null && myTTWDest != null) {
                    int from = myTTWNow.turns;
                    int to = myTTWDest.turns;
                    if (from > to) {
                        if ((from == 2 && to == 1))
                            score += 6;
                        else if (to == 0)
                            score += 10;
                        else
                            score += 4; // small improvement otherwise
                    }
                }

                // Enemy engagement shaping (only if dest not threatened)
                if (!tactical.threats.isThreatened(dr, dc) && !tactical.enemies.isEmpty()) {
                    int newDist = nearestManhattan(dr, dc, tactical.enemies);
                    if (newDist < currentDist)
                        score += Math.min(6, currentDist - newDist);
                }

                if (score > bestScore) {
                    bestScore = score;
                    bestDest = p;
                    bestIdx = idx;
                }
            }

            // Only emit if bestDest is a different tile (real action); otherwise we
            // implicitly stand still
            if (bestDest != null && !(bestIdx[0] == pos.row() && bestIdx[1] == pos.col())) {
                final Coord src = pos;
                final Plot dest = bestDest;
                final GamePiece ref = me;
                final int dr = bestIdx[0], dc = bestIdx[1];
                int finalScore = bestScore;
                out.add(new Intent(finalScore, () -> moveAndVerify(b, src.row(), src.col(), dest, ref, dr, dc),
                        IntentType.MANEUVER));
            }
        }
    }

    private void buildSummonIntents(Board b, TacticalState tactical, List<Intent> out) {
        var ps = PlayerManager.get(PieceAlignment.P2);
        if (ps == null || ps.hand == null)
            return;
        int mana = ps.mana;
        // Determine threatened column closest to our home row
        int homeRow = b.getROWS() - 1;
        int cols = b.getCOLS();
        int bestCol = -1, bestDist = Integer.MAX_VALUE;
        for (PieceEntry entry : tactical.enemies) {
            Coord pos = entry.pos;
            int dist = Math.abs(homeRow - pos.row());
            if (dist < bestDist) {
                bestDist = dist;
                bestCol = pos.col();
            }
        }
        // Collect candidate plots (preferred col, else any home-row plot)
        List<Plot> plots = new ArrayList<>();
        if (bestCol != -1) {
            Renderable rp = b.getPlotAtPos(homeRow, bestCol);
            if (rp instanceof Plot p && b.isValidSummonTarget(p, PieceAlignment.P2))
                plots.add(p);
        }
        if (plots.isEmpty()) {
            for (int c = 0; c < cols; c++) {
                Renderable rp = b.getPlotAtPos(homeRow, c);
                if (rp instanceof Plot p && b.isValidSummonTarget(p, PieceAlignment.P2))
                    plots.add(p);
            }
        }
        if (plots.isEmpty())
            return;
        // Choose best affordable card using base stats + cost
        SummonCard bestCard = null;
        int bestScore = Integer.MIN_VALUE;
        for (Card c : ps.hand.getCards()) {
            if (!(c instanceof SummonCard sc))
                continue;
            int cost = sc.getStats().getCost();
            if (cost > mana)
                continue;
            var s = sc.getStats();
            int value = (s.getMaxHealth() * 2) + (s.getDamage() * 3) + (s.getActions()) + (s.getSpeed()) + (cost); // simple
                                                                                                                   // heuristic
            if (value > bestScore) {
                bestScore = value;
                bestCard = sc;
            }
        }
        if (bestCard == null)
            return;
        Plot dest = plots.get(0);
        final SummonCard card = bestCard;
        final Plot target = dest;
        int finalScore = SCORE_SUMMON_BASE + Math.min(30, bestScore / 2);
        out.add(new Intent(finalScore, () -> summonAndVerify(b, card, target), IntentType.DEF_SUMMON));
    }

    private boolean moveAndVerify(Board b, int sr, int sc, Plot dest, GamePiece ref, int dr, int dc) {
        Renderable srcR = b.getPlotAtPos(sr, sc);
        if (!(srcR instanceof Plot srcPlot))
            return false;
        HashMap<Integer, CustomBox> entities = new HashMap<>();
        entities.put(0, srcPlot);
        entities.put(1, dest);
        srcPlot.triggerClickEffect(entities);
        GamePiece after = b.getGamePieceAtPos(dr, dc);
        return after == ref;
    }

    private boolean attackAndVerify(Board b, Coord src, Coord target, MonsterGamePiece attacker) {
        Renderable srcR = b.getPlotAtPos(src.row(), src.col());
        Renderable dstR = b.getPlotAtPos(target.row(), target.col());
        if (!(srcR instanceof Plot srcPlot) || !(dstR instanceof Plot dstPlot))
            return false;

        GamePiece defenderBefore = b.getGamePieceAtPos(target.row(), target.col());
        int actionsBefore = getRemainingActions(attacker);

        HashMap<Integer, CustomBox> entities = new HashMap<>();
        entities.put(0, srcPlot);
        entities.put(1, dstPlot);
        srcPlot.triggerClickEffect(entities);

        GamePiece defenderAfter = b.getGamePieceAtPos(target.row(), target.col());
        if (defenderBefore != null && defenderAfter != defenderBefore)
            return true; // killed or moved

        int actionsAfter = getRemainingActions(attacker);
        return actionsAfter < actionsBefore; // action spent implies a hit
    }

    private boolean summonAndVerify(Board b, SummonCard card, Plot p) {
        var ps = PlayerManager.get(PieceAlignment.P2);
        int beforeSize = ps.hand.getCards().size();
        int beforeMana = ps.mana;
        HashMap<Integer, CustomBox> entities = new HashMap<>();
        entities.put(0, card);
        entities.put(1, p);
        card.triggerClickEffect(entities);
        boolean consumed = !ps.hand.getCards().contains(card) || ps.hand.getCards().size() < beforeSize;
        boolean spentMana = ps.mana < beforeMana;
        return consumed || spentMana;
    }

    // --- Turn end checks and helpers ---
    private boolean shouldEndNow(Board b) {
        // If any of our pieces has an actionable option (attack or move) with actions
        // remaining, keep going
        int rows = b.getROWS(), cols = b.getCOLS();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                GamePiece gp = b.getGamePieceAtPos(r, c);
                if (!(gp instanceof MonsterGamePiece me) || me.getAlignment() != PieceAlignment.P2)
                    continue;
                if (!canAct(me))
                    continue;
                // attack available?
                List<Plot> hostile = b.getAttackableEnemyPlots(r, c, PieceAlignment.P2);
                if (hostile != null && !hostile.isEmpty())
                    return false;
                // can move somewhere?
                int speed = me.getStats().getSpeed();
                if (speed > 0) {
                    List<Plot> reach = b.getReachablePlots(r, c, speed);
                    if (reach != null && !reach.isEmpty())
                        return false;
                }
            }
        }
        // No unit can act; if we have a plausible summon candidate, keep going
        if (hasSummonCandidate(b))
            return false;
        return true;
    }

    private int getRemainingActions(MonsterGamePiece mgp) {
        return mgp.getStats().getRemainingActions();
    }

    private boolean canAct(MonsterGamePiece mgp) {
        if (mgp == null)
            return false;
        // Stunned pieces cannot act
        if (mgp.isStunned())
            return false;
        // Must have remaining actions
        return getRemainingActions(mgp) > 0;
    }

    private boolean hasSummonCandidate(Board b) {
        var ps = PlayerManager.get(PieceAlignment.P2);
        if (ps == null || ps.hand == null)
            return false;
        int mana = ps.mana;
        boolean hasAffordable = false;
        for (Card c : ps.hand.getCards()) {
            if (c instanceof SummonCard sc && sc.getStats().getCost() <= mana) {
                hasAffordable = true;
                break;
            }
        }
        if (!hasAffordable)
            return false;
        int homeRow = b.getROWS() - 1;
        for (int col = 0; col < b.getCOLS(); col++) {
            Renderable rp = b.getPlotAtPos(homeRow, col);
            if (rp instanceof Plot p && b.isValidSummonTarget(p, PieceAlignment.P2))
                return true;
        }
        return false;
    }

    // --- Win detection ---

    private boolean wouldEnableLethal(MonsterGamePiece me, Board b, int toR, int toC) {
        // Check if from (toR,toC) we have an adjacent enemy that can be killed with one
        // attack
        List<int[]> adj = List.of(new int[] { toR + 1, toC }, new int[] { toR - 1, toC }, new int[] { toR, toC + 1 },
                new int[] { toR, toC - 1 });
        for (int[] a : adj) {
            if (!inBounds(b, a[0], a[1]))
                continue;
            GamePiece gp = b.getGamePieceAtPos(a[0], a[1]);
            if (gp instanceof MonsterGamePiece em && em.getAlignment() == PieceAlignment.P1) {
                int hp = em.getStats().getCurrentHealth();
                if (me.getEffectiveDamage() >= hp)
                    return true;
            }
        }
        return false;
    }

    private boolean inBounds(Board b, int r, int c) {
        return r >= 0 && r < b.getROWS() && c >= 0 && c < b.getCOLS();
    }

    // --- Threat map ---

    private ThreatMap computeThreatMap(Board b, PieceAlignment enemySide) {
        int rows = b.getROWS(), cols = b.getCOLS();
        ThreatMap map = new ThreatMap(rows, cols);
        // For each enemy with non-zero attack, mark tiles they can attack NEXT TURN
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                GamePiece gp = b.getGamePieceAtPos(r, c);
                if (!(gp instanceof MonsterGamePiece em) || em.getAlignment() != enemySide)
                    continue;
                // Skip enemies that cannot deal damage
                if (em.getEffectiveDamage() <= 0)
                    continue;
                int actions = em.getEffectiveActions();
                if (actions <= 0)
                    continue;
                int speed = em.getEffectiveSpeed();
                // Movement steps available before an attack next turn
                int moveStepsForAttack = Math.max(0, (actions - 1) * Math.max(0, speed));
                // BFS flood up to moveStepsForAttack steps through empty tiles (cannot pass
                // through any piece)
                Queue<int[]> q = new ArrayDeque<>();
                boolean[][] seen = new boolean[rows][cols];
                q.add(new int[] { r, c, 0 });
                seen[r][c] = true;
                final int[][] dirs = new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
                while (!q.isEmpty()) {
                    int[] cur = q.poll();
                    int cr = cur[0], cc = cur[1], d = cur[2];
                    if (d > moveStepsForAttack)
                        continue;
                    // From any tile we can occupy after moving 'd' steps (including start), we can
                    // attack its 4-adjacent tiles
                    for (int[] dir : dirs) {
                        int ar = cr + dir[0], ac = cc + dir[1];
                        map.mark(ar, ac);
                    }
                    if (d == moveStepsForAttack)
                        continue;
                    // Continue flood through empty tiles only
                    for (int[] dir : dirs) {
                        int nr = cr + dir[0], nc = cc + dir[1];
                        if (nr < 0 || nc < 0 || nr >= rows || nc >= cols || seen[nr][nc])
                            continue;
                        // cannot traverse through occupied tiles
                        if (b.getGamePieceAtPos(nr, nc) != null)
                            continue;
                        seen[nr][nc] = true;
                        q.add(new int[] { nr, nc, d + 1 });
                    }
                }
            }
        return map;
    }

    // Check if tile (r,c) would be one-shot lethal for 'me' on the enemy's next
    // turn
    private boolean isLethalThreatNextTurn(Board b, MonsterGamePiece me, int r, int c) {
        int rows = b.getROWS(), cols = b.getCOLS();
        int myHp = Math.max(0, me.getStats().getCurrentHealth());
        PieceAlignment enemySide = (me.getAlignment() == PieceAlignment.P1) ? PieceAlignment.P2 : PieceAlignment.P1;
        for (int er = 0; er < rows; er++)
            for (int ec = 0; ec < cols; ec++) {
                GamePiece gp = b.getGamePieceAtPos(er, ec);
                if (!(gp instanceof MonsterGamePiece em) || em.getAlignment() != enemySide)
                    continue;
                int dmg = em.getEffectiveDamage();
                if (dmg <= 0)
                    continue; // non-attacking pieces pose no lethal threat
                int actions = em.getEffectiveActions();
                if (actions <= 0)
                    continue;
                int speed = em.getStats().getSpeed();
                int T = Math.max(1, (actions - 1) * speed + 1);
                // BFS to enumerate end positions the enemy can occupy by end of its next turn
                Queue<int[]> q = new ArrayDeque<>();
                boolean[][] seen = new boolean[rows][cols];
                q.add(new int[] { er, ec, 0 });
                seen[er][ec] = true;
                while (!q.isEmpty()) {
                    int[] cur = q.poll();
                    int cr = cur[0], cc = cur[1], d = cur[2];
                    if (d > T)
                        continue;
                    // If from (cr,cc) the enemy can attack (r,c) next turn (adjacent), check lethal
                    if (Math.abs(cr - r) + Math.abs(cc - c) == 1) {
                        if (dmg >= myHp)
                            return true;
                    }
                    if (d == T)
                        continue;
                    int[][] dirs = new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
                    for (int[] dir : dirs) {
                        int nr = cr + dir[0], nc = cc + dir[1];
                        if (nr < 0 || nc < 0 || nr >= rows || nc >= cols || seen[nr][nc])
                            continue;
                        if (b.getGamePieceAtPos(nr, nc) != null)
                            continue; // cannot pass through pieces
                        seen[nr][nc] = true;
                        q.add(new int[] { nr, nc, d + 1 });
                    }
                }
            }
        return false;
    }

    private boolean hasAbility(MonsterGamePiece mgp, String simplicityName) {
        if (mgp == null)
            return false;
        try {
            for (var ab : mgp.getAbilities()) {
                if (ab != null && ab.getClass().getSimpleName().equals(simplicityName)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean isRogue(MonsterGamePiece mgp) {
        return hasAbility(mgp, "RogueFreeStrikeAbility");
    }
}
