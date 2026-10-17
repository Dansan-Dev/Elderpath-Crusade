package io.github.elderpath_crusade.bot.eval;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.BoardGrid;
import io.github.elderpath_crusade.bot.eval.BotActionContext.Intent;
import io.github.elderpath_crusade.bot.eval.BotActionContext.IntentType;
import io.github.elderpath_crusade.bot.eval.BotActionContext.PieceEntry;
import io.github.elderpath_crusade.bot.eval.BotActionContext.TacticalState;
import io.github.elderpath_crusade.bot.eval.BotActionContext.WinPathResult;
import io.github.elderpath_crusade.bot.search.BotSearchState;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.bot.search.ThreatMap;

import java.util.*;

/**
 * Generates and scores movement-based intents: win-path discovery, advancement
 * toward enemies,
 * and tactical maneuvers for decongestion and safety.
 */
public class MovementEvaluator extends BotEvaluatorBase {

    private final Map<String, List<Coord>> reachableCache = new HashMap<>();

    // The side being played, set per build() — this evaluator is alignment-agnostic.
    private PieceAlignment self = PieceAlignment.P2;
    private PieceAlignment enemy = PieceAlignment.P1;
    private int goalRow = 0;

    public MovementEvaluator(BotConfig config) {
        super(config);
    }

    public void clearCache() {
        reachableCache.clear();
    }

    private List<Coord> getReachableCached(BoardGrid board, Entity entity, int row, int col, int speed) {
        String key = EntityUtils.getId(entity) + '_' + row + '_' + col;
        return reachableCache.computeIfAbsent(key, k -> board.getReachable(entity, row, col, speed));
    }

    @Override
    public void build(BoardGrid board, TacticalState tactical, List<Intent> output) {
        clearCache();
        self = tactical.self();
        enemy = tactical.enemy();
        goalRow = board.getGoalRow(self);
        buildWinPathIntents(board, tactical, output);
        buildAdvanceIntents(board, tactical, output);
        buildManeuverIntents(board, tactical, output);
    }

    private void buildWinPathIntents(BoardGrid board, TacticalState tactical, List<Intent> output) {
        for (PieceEntry entry : tactical.allies()) {
            Entity entity = entry.entity();
            Coord pos = entry.pos();
            if (!canAct(entity)) {
                continue;
            }

            WinPathResult result = estimateTurnsToGoal(board, entity, pos.row(), pos.col(), tactical.threats(),
                    getRemainingActions(entity));
            if (result == null || result.turns() > 2 || result.firstMove() == null) {
                continue;
            }

            int baseScore = switch (result.turns()) {
                case 0 -> config.scoreWinNow();
                case 1 -> config.scoreWinPath1();
                default -> config.scoreWinPath2();
            };

            int penalty = calculateWinPathPenalty(board, entity, result, tactical.threats());
            int finalScore = Math.max(config.scoreWinPathMin(), baseScore - penalty);

            Coord destination = inBoundsOrNull(board, result.firstMove());
            if (destination == null) {
                continue;
            }

            final Coord finalWinPlot = destination;
            final Coord firstMove = result.firstMove();
            IntentType kind = (result.turns() == 0) ? IntentType.WIN_MOVE
                    : (result.turns() == 1 ? IntentType.WIN_PATH1 : IntentType.WIN_PATH2);
            Entity reference = board.getEntityAt(pos.row(), pos.col());
            output.add(new Intent(finalScore,
                    () -> moveAndVerify(board, tactical.self(), pos.row(), pos.col(), finalWinPlot, reference, firstMove.row(),
                            firstMove.col()),
                    kind));
        }
    }

    private void buildAdvanceIntents(BoardGrid board, TacticalState tactical, List<Intent> output) {
        if (tactical.enemies().isEmpty()) {
            return;
        }

        for (PieceEntry entry : tactical.allies()) {
            Entity entity = entry.entity();
            Coord pos = entry.pos();
            if (!canAct(entity)) {
                continue;
            }

            int speed = EntityUtils.getSpeed(entity);
            List<Coord> reachable = getReachableCached(board, entity, pos.row(), pos.col(), speed);
            int currentDist = nearestManhattan(pos.row(), pos.col(), tactical.enemies());
            boolean hasRogue = isRogue(entity);
            Entity reference = board.getEntityAt(pos.row(), pos.col());

            Coord bestPlot = null;
            int bestDist = currentDist;

            // Rogue special: if moving to any reachable tile yields an immediate lethal
            // free strike, prefer it
            if (hasRogue && reachable != null && !reachable.isEmpty()) {
                Coord lethalDest = null;
                int bestForwardBias = -9999;
                for (Coord p : reachable) {
                    List<Coord> attacks = board.getAttackableEnemies(p.row(), p.col(), self);
                    if (attacks == null || attacks.isEmpty())
                        continue;

                    boolean lethalExists = false;
                    for (Coord targetPlot : attacks) {
                        Entity target = board.getEntityAt(targetPlot.row(), targetPlot.col());
                        if (target != null) {
                            if (EntityUtils.getDamage(entity) >= Math.max(0,
                                    EntityUtils.getCurrentHealth(target))) {
                                lethalExists = true;
                                break;
                            }
                        }
                    }

                    if (lethalExists) {
                        int forwardBias = forwardness(board, self, pos.row(), p.row());
                        if (lethalDest == null || forwardBias > bestForwardBias) {
                            lethalDest = p;
                            bestForwardBias = forwardBias;
                        }
                    }
                }

                if (lethalDest != null) {
                    final Coord finalLethalDest = lethalDest;
                    int score = config.scoreRogueLethalMove();
                    if (forwardness(board, self, pos.row(), finalLethalDest.row()) > 0)
                        score += config.dirForwardBonus();
                    else if (forwardness(board, self, pos.row(), finalLethalDest.row()) < 0)
                        score -= config.dirBackwardPenalty();

                    output.add(new Intent(score,
                            () -> moveAndVerify(board, tactical.self(), pos.row(), pos.col(), finalLethalDest, reference,
                                    finalLethalDest.row(), finalLethalDest.col()),
                            IntentType.ADVANCE));
                    continue;
                }
            }

            // Generic pathing: find move that gets us closer to enemies without stepping
            // into threat
            if (reachable != null) {
                for (Coord p : reachable) {
                    int d = nearestManhattan(p.row(), p.col(), tactical.enemies());
                    boolean threatened = tactical.threats().isThreatened(p.row(), p.col());
                    boolean allowRogueThreat = false;

                    if (threatened && hasRogue) {
                        List<Coord> attacks = board.getAttackableEnemies(p.row(), p.col(), self);
                        allowRogueThreat = attacks != null && !attacks.isEmpty();
                    }

                    if (d < bestDist && (!threatened || allowRogueThreat)) {
                        bestPlot = p;
                        bestDist = d;
                    }
                }
            }

            // Desperation/Aggression: if no safe move closer, allow moves that enable
            // lethal next turn
            if (bestPlot == null && reachable != null) {
                for (Coord p : reachable) {
                    if (wouldEnableLethal(board, entity, p.row(), p.col())) {
                        bestPlot = p;
                        bestDist = nearestManhattan(p.row(), p.col(), tactical.enemies());
                        break;
                    }
                }
            }

            if (bestPlot != null) {
                final Coord finalBestPlot = bestPlot;
                int gain = Math.max(0, currentDist - bestDist);
                int score = config.scoreAdvanceBase() + Math.min(10, gain);
                if (forwardness(board, self, pos.row(), finalBestPlot.row()) > 0)
                    score += config.dirForwardBonus();
                else if (forwardness(board, self, pos.row(), finalBestPlot.row()) < 0)
                    score -= config.dirBackwardPenalty();

                if (isRogue(entity)) {
                    List<Coord> attacks = board.getAttackableEnemies(finalBestPlot.row(), finalBestPlot.col(),
                            self);
                    if (attacks != null && !attacks.isEmpty()) {
                        int bonus = config.bonusRogueFreeStrike();
                        boolean lethalExists = false;
                        for (Coord targetPlot : attacks) {
                            Entity target = board.getEntityAt(targetPlot.row(), targetPlot.col());
                            if (target != null) {
                                if (EntityUtils.getDamage(entity) >= Math.max(0,
                                        EntityUtils.getCurrentHealth(target))) {
                                    lethalExists = true;
                                    break;
                                }
                            }
                        }
                        if (lethalExists)
                            bonus += config.bonusRogueLethal();
                        score += bonus;
                    }
                }

                output.add(new Intent(score,
                        () -> moveAndVerify(board, tactical.self(), pos.row(), pos.col(), finalBestPlot, reference, finalBestPlot.row(),
                                finalBestPlot.col()),
                        IntentType.ADVANCE));
            }
        }
    }

    private void buildManeuverIntents(BoardGrid board, TacticalState tactical, List<Intent> output) {
        int rows = board.getRows();
        for (PieceEntry entry : tactical.allies()) {
            Entity entity = entry.entity();
            Coord pos = entry.pos();
            if (!canAct(entity)) {
                continue;
            }

            int actionsRemaining = getRemainingActions(entity);
            List<Coord> reachable = getReachableCached(board, entity, pos.row(), pos.col(), EntityUtils.getSpeed(entity));
            Coord stayPlot = inBoundsOrNull(board, pos);
            Entity reference = board.getEntityAt(pos.row(), pos.col());

            List<Coord> candidates = new ArrayList<>();
            if (stayPlot != null)
                candidates.add(stayPlot);
            if (reachable != null)
                candidates.addAll(reachable);

            boolean underAdjThreatNow = tactical.threats().isThreatened(pos.row(), pos.col());
            int bestScore = Integer.MIN_VALUE;
            Coord bestPlot = null;

            // Lane decongestion scan
            int allyWeightInLane = -1;
            int back = -forwardness(board, self, 0, 1); // row step toward our own home row
            for (int r = pos.row() + back; r >= 0 && r < rows; r += back) {
                Entity allyBehind = board.getEntityAt(r, pos.col());
                if (allyBehind != null && EntityUtils.getAlignment(allyBehind) == self) {
                    allyWeightInLane = EntityUtils.getDamage(allyBehind) * 2 + EntityUtils.getActions(allyBehind)
                            + EntityUtils.getSpeed(allyBehind);
                    WinPathResult myTTW = estimateTurnsToGoal(board, entity, pos.row(), pos.col(), tactical.threats(),
                            actionsRemaining);
                    WinPathResult allyTTW = estimateTurnsToGoal(board, allyBehind, r, pos.col(), tactical.threats(),
                            getRemainingActions(allyBehind));
                    if (myTTW != null && allyTTW != null
                            && allyTTW.turns() < (myTTW.turns() == 0 ? 0 : myTTW.turns())) {
                        allyWeightInLane += 6;
                    }
                    break;
                } else if (board.getEntityAt(r, pos.col()) != null)
                    break;
            }

            int currentDist = tactical.enemies().isEmpty() ? Integer.MAX_VALUE
                    : nearestManhattan(pos.row(), pos.col(), tactical.enemies());
            WinPathResult myTTWNow = estimateTurnsToGoal(board, entity, pos.row(), pos.col(), tactical.threats(),
                    actionsRemaining);

            for (Coord p : candidates) {
                int dr = p.row();
                int dc = p.col();
                if (isLethalThreatNextTurn(board, entity, dr, dc))
                    continue;

                int score = config.scoreManeuverBase();
                if (tactical.threats().isThreatened(dr, dc)) {
                    score -= (config.penaltyThreatBase()
                            + Math.min(15, 5 * Math.max(0, tactical.threats().getCount(dr, dc) - 1)));
                }

                if (forwardness(board, self, pos.row(), dr) > 0)
                    score += config.dirForwardBonus();
                else if (forwardness(board, self, pos.row(), dr) < 0)
                    score -= config.dirBackwardPenalty();

                if (underAdjThreatNow && !(dr == pos.row() && dc == pos.col())) {
                    boolean exitsThreat = !tactical.threats().isThreatened(dr, dc);
                    int unblockBonus = (allyWeightInLane != -1 && dc != pos.col())
                            ? (8 + Math.min(7, allyWeightInLane / 4))
                            : 0;
                    if (!(exitsThreat && unblockBonus >= 10))
                        score -= 12;
                    else
                        score += unblockBonus;
                } else if (!underAdjThreatNow && allyWeightInLane != -1) {
                    if (dc == pos.col())
                        score -= (6 + Math.min(10, allyWeightInLane / 3));
                    else
                        score += (8 + Math.min(7, allyWeightInLane / 4));
                }

                int actionsAfter = actionsRemaining - ((dr == pos.row() && dc == pos.col()) ? 0 : 1);
                WinPathResult myTTWDest = estimateTurnsToGoal(board, entity, dr, dc, tactical.threats(),
                        Math.max(0, actionsAfter));
                if (myTTWNow != null && myTTWDest != null && myTTWNow.turns() > myTTWDest.turns()) {
                    if (myTTWNow.turns() == 2 && myTTWDest.turns() == 1)
                        score += 6;
                    else if (myTTWDest.turns() == 0)
                        score += 10;
                    else
                        score += 4;
                }

                if (!tactical.threats().isThreatened(dr, dc) && !tactical.enemies().isEmpty()) {
                    int newDist = nearestManhattan(dr, dc, tactical.enemies());
                    if (newDist < currentDist)
                        score += Math.min(6, currentDist - newDist);
                }

                if (score > bestScore) {
                    bestScore = score;
                    bestPlot = p;
                }
            }

            if (bestPlot != null && !(bestPlot.row() == pos.row() && bestPlot.col() == pos.col())) {
                final Coord finalManeuverPlot = bestPlot;
                output.add(new Intent(bestScore,
                        () -> moveAndVerify(board, tactical.self(), pos.row(), pos.col(), finalManeuverPlot, reference,
                                finalManeuverPlot.row(), finalManeuverPlot.col()),
                        IntentType.MANEUVER));
            }
        }
    }

    private int calculateWinPathPenalty(BoardGrid board, Entity entity, WinPathResult result, ThreatMap threats) {
        int penalty = Math.min(config.penaltyWinPathExposureMax(),
                result.threatExposure() * config.penaltyWinPathExposureScale());
        if (result.turns() == 0)
            return penalty;

        if (isLethalThreatNextTurn(board, entity, result.firstMove().row(), result.firstMove().col())) {
            penalty += config.penaltyLethalExposure();
        }

        if (result.endsTurn0InThreat()) {
            int attackerCount = threats.getCount(result.firstMove().row(), result.firstMove().col());
            penalty += config.penaltyWinPathEndThreatBase() + Math.min(config.penaltyWinPathEndThreatExtraMax(),
                    config.penaltyWinPathEndThreatExtraScale() * Math.max(0, attackerCount - 1));
        }
        return penalty;
    }

    private WinPathResult estimateTurnsToGoal(BoardGrid board, Entity entity, int row, int col, ThreatMap threats,
            int actions) {
        int speed = EntityUtils.getSpeed(entity);
        int maxActions = EntityUtils.getActions(entity);
        String entityId = EntityUtils.getId(entity);
        Deque<BotSearchState> queue = new ArrayDeque<>();
        Set<Integer> visited = new HashSet<>();

        Coord startPos = new Coord(row, col);
        BotSearchState start = new BotSearchState(startPos, 0, actions, 0, null, false);
        queue.add(start);
        visited.add(start.pack());

        int expansions = 0;
        while (!queue.isEmpty() && expansions < config.maxWinExpansions()) {
            BotSearchState state = queue.poll();
            if (state.pos.row() == goalRow) {
                return new WinPathResult(state.turnDepth, state.firstMove, state.threatCount,
                        state.endsFirstTurnInThreat);
            }
            if (state.turnDepth > 2)
                continue;

            // Turn rollover
            if (state.turnDepth < 2) {
                BotSearchState nextTurn = state.waitTurn(threats, maxActions);
                if (visited.add(nextTurn.pack()))
                    queue.add(nextTurn);
            }

            // Movement options
            if (state.actionsLeft > 0) {
                String cacheKey = entityId + "_" + state.pos.row() + "_" + state.pos.col();
                List<Coord> reachablePlots = reachableCache.computeIfAbsent(cacheKey,
                        k -> board.getReachable(entity, state.pos.row(), state.pos.col(), speed));
                for (Coord plot : reachablePlots) {
                    Coord next = plot;
                    expansions++;
                    BotSearchState nextStep = state.stepTo(next);
                    if (visited.add(nextStep.pack()))
                        queue.add(nextStep);
                    if (expansions >= config.maxWinExpansions())
                        break;
                }
            }
        }
        return null;
    }

    private boolean wouldEnableLethal(BoardGrid board, Entity entity, int row, int col) {
        int[][] directions = new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int[] dir : directions) {
            int nr = row + dir[0];
            int nc = col + dir[1];
            if (inBounds(board, nr, nc)) {
                Entity target = board.getEntityAt(nr, nc);
                if (target != null && EntityUtils.getAlignment(target) == enemy) {
                    if (EntityUtils.getDamage(entity) >= EntityUtils.getCurrentHealth(target))
                        return true;
                }
            }
        }
        return false;
    }

    private Coord inBoundsOrNull(BoardGrid board, Coord coord) {
        return board.isInBounds(coord.row(), coord.col()) ? coord : null;
    }
}
