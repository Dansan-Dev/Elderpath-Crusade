package io.github.elderpath_crusade.managers.bot.eval;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.managers.bot.eval.BotActionContext.Intent;
import io.github.elderpath_crusade.managers.bot.eval.BotActionContext.IntentType;
import io.github.elderpath_crusade.managers.bot.eval.BotActionContext.PieceEntry;
import io.github.elderpath_crusade.managers.bot.eval.BotActionContext.TacticalState;
import io.github.elderpath_crusade.managers.bot.eval.BotActionContext.WinPathResult;
import io.github.elderpath_crusade.managers.bot.search.BotSearchState;
import io.github.elderpath_crusade.managers.bot.search.Coord;
import io.github.elderpath_crusade.managers.bot.search.ThreatMap;

import java.util.*;

/**
 * Generates and scores movement-based intents: win-path discovery, advancement
 * toward enemies,
 * and tactical maneuvers for decongestion and safety.
 */
public class MovementEvaluator extends BotEvaluatorBase {

    public MovementEvaluator(BotConfig config) {
        super(config);
    }

    @Override
    public void build(Board board, TacticalState tactical, List<Intent> output) {
        buildWinPathIntents(board, tactical, output);
        buildAdvanceIntents(board, tactical, output);
        buildManeuverIntents(board, tactical, output);
    }

    private void buildWinPathIntents(Board board, TacticalState tactical, List<Intent> output) {
        for (PieceEntry entry : tactical.allies()) {
            MonsterGamePiece piece = entry.piece();
            Coord pos = entry.pos();
            if (!canAct(piece)) {
                continue;
            }

            WinPathResult result = estimateTurnsToRow0(board, piece, pos.row(), pos.col(), tactical.threats(),
                    getRemainingActions(piece));
            if (result == null || result.turns() > 2 || result.firstMove() == null) {
                continue;
            }

            int baseScore = switch (result.turns()) {
                case 0 -> config.scoreWinNow();
                case 1 -> config.scoreWinPath1();
                default -> config.scoreWinPath2();
            };

            int penalty = calculateWinPathPenalty(board, piece, result, tactical.threats());
            int finalScore = Math.max(config.scoreWinPathMin(), baseScore - penalty);

            Plot destination = getPlot(board, result.firstMove());
            if (destination == null) {
                continue;
            }

            final Plot finalWinPlot = destination;
            final Coord firstMove = result.firstMove();
            IntentType kind = (result.turns() == 0) ? IntentType.WIN_MOVE
                    : (result.turns() == 1 ? IntentType.WIN_PATH1 : IntentType.WIN_PATH2);
            output.add(new Intent(finalScore,
                    () -> moveAndVerify(board, pos.row(), pos.col(), finalWinPlot, piece, firstMove.row(),
                            firstMove.col()),
                    kind));
        }
    }

    private void buildAdvanceIntents(Board board, TacticalState tactical, List<Intent> output) {
        if (tactical.enemies().isEmpty()) {
            return;
        }

        for (PieceEntry entry : tactical.allies()) {
            MonsterGamePiece piece = entry.piece();
            Coord pos = entry.pos();
            if (!canAct(piece)) {
                continue;
            }

            int speed = piece.getEffectiveSpeed();
            List<Plot> reachable = board.getReachablePlots(pos.row(), pos.col(), speed);
            int currentDist = nearestManhattan(pos.row(), pos.col(), tactical.enemies());
            boolean hasRogue = isRogue(piece);

            Plot bestPlot = null;
            int bestDist = currentDist;

            // Rogue special: if moving to any reachable tile yields an immediate lethal
            // free strike, prefer it
            if (hasRogue && reachable != null && !reachable.isEmpty()) {
                Plot lethalDest = null;
                int bestForwardBias = -9999;
                for (Plot p : reachable) {
                    List<Plot> attacks = board.getAttackableEnemyPlots(p.getRow(), p.getCol(), PieceAlignment.P2);
                    if (attacks == null || attacks.isEmpty())
                        continue;

                    boolean lethalExists = false;
                    for (Plot targetPlot : attacks) {
                        GamePiece target = board.getGamePieceAtPos(targetPlot.getRow(), targetPlot.getCol());
                        if (target instanceof MonsterGamePiece targetMonster) {
                            if (piece.getEffectiveDamage() >= Math.max(0,
                                    targetMonster.getStats().getCurrentHealth())) {
                                lethalExists = true;
                                break;
                            }
                        }
                    }

                    if (lethalExists) {
                        int forwardBias = (p.getRow() < pos.row() ? 1 : (p.getRow() > pos.row() ? -1 : 0));
                        if (lethalDest == null || forwardBias > bestForwardBias) {
                            lethalDest = p;
                            bestForwardBias = forwardBias;
                        }
                    }
                }

                if (lethalDest != null) {
                    final Plot finalLethalDest = lethalDest;
                    int score = config.scoreRogueLethalMove();
                    if (finalLethalDest.getRow() < pos.row())
                        score += config.dirForwardBonus();
                    else if (finalLethalDest.getRow() > pos.row())
                        score -= config.dirBackwardPenalty();

                    output.add(new Intent(score,
                            () -> moveAndVerify(board, pos.row(), pos.col(), finalLethalDest, piece,
                                    finalLethalDest.getRow(), finalLethalDest.getCol()),
                            IntentType.ADVANCE));
                    continue;
                }
            }

            // Generic pathing: find move that gets us closer to enemies without stepping
            // into threat
            if (reachable != null) {
                for (Plot p : reachable) {
                    int d = nearestManhattan(p.getRow(), p.getCol(), tactical.enemies());
                    boolean threatened = tactical.threats().isThreatened(p.getRow(), p.getCol());
                    boolean allowRogueThreat = false;

                    if (threatened && hasRogue) {
                        List<Plot> attacks = board.getAttackableEnemyPlots(p.getRow(), p.getCol(), PieceAlignment.P2);
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
                for (Plot p : reachable) {
                    if (wouldEnableLethal(board, piece, p.getRow(), p.getCol())) {
                        bestPlot = p;
                        bestDist = nearestManhattan(p.getRow(), p.getCol(), tactical.enemies());
                        break;
                    }
                }
            }

            if (bestPlot != null) {
                final Plot finalBestPlot = bestPlot;
                int gain = Math.max(0, currentDist - bestDist);
                int score = config.scoreAdvanceBase() + Math.min(10, gain);
                if (finalBestPlot.getRow() < pos.row())
                    score += config.dirForwardBonus();
                else if (finalBestPlot.getRow() > pos.row())
                    score -= config.dirBackwardPenalty();

                if (isRogue(piece)) {
                    List<Plot> attacks = board.getAttackableEnemyPlots(finalBestPlot.getRow(), finalBestPlot.getCol(),
                            PieceAlignment.P2);
                    if (attacks != null && !attacks.isEmpty()) {
                        int bonus = config.bonusRogueFreeStrike();
                        boolean lethalExists = false;
                        for (Plot targetPlot : attacks) {
                            GamePiece target = board.getGamePieceAtPos(targetPlot.getRow(), targetPlot.getCol());
                            if (target instanceof MonsterGamePiece targetMonster) {
                                if (piece.getEffectiveDamage() >= Math.max(0,
                                        targetMonster.getStats().getCurrentHealth())) {
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
                        () -> moveAndVerify(board, pos.row(), pos.col(), finalBestPlot, piece, finalBestPlot.getRow(),
                                finalBestPlot.getCol()),
                        IntentType.ADVANCE));
            }
        }
    }

    private void buildManeuverIntents(Board board, TacticalState tactical, List<Intent> output) {
        int rows = board.getROWS();
        for (PieceEntry entry : tactical.allies()) {
            MonsterGamePiece piece = entry.piece();
            Coord pos = entry.pos();
            if (!canAct(piece)) {
                continue;
            }

            int actionsRemaining = getRemainingActions(piece);
            List<Plot> reachable = board.getReachablePlots(pos.row(), pos.col(), piece.getEffectiveSpeed());
            Plot stayPlot = getPlot(board, pos);

            List<Plot> candidates = new ArrayList<>();
            if (stayPlot != null)
                candidates.add(stayPlot);
            if (reachable != null)
                candidates.addAll(reachable);

            boolean underAdjThreatNow = tactical.threats().isThreatened(pos.row(), pos.col());
            int bestScore = Integer.MIN_VALUE;
            Plot bestPlot = null;

            // Lane decongestion scan
            int allyWeightInLane = -1;
            for (int r = pos.row() + 1; r < rows; r++) {
                GamePiece g2 = board.getGamePieceAtPos(r, pos.col());
                if (g2 instanceof MonsterGamePiece m2 && m2.getAlignment() == PieceAlignment.P2) {
                    allyWeightInLane = m2.getStats().getDamage() * 2 + m2.getStats().getActions()
                            + m2.getStats().getSpeed();
                    WinPathResult myTTW = estimateTurnsToRow0(board, piece, pos.row(), pos.col(), tactical.threats(),
                            actionsRemaining);
                    WinPathResult allyTTW = estimateTurnsToRow0(board, m2, r, pos.col(), tactical.threats(),
                            getRemainingActions(m2));
                    if (myTTW != null && allyTTW != null
                            && allyTTW.turns() < (myTTW.turns() == 0 ? 0 : myTTW.turns())) {
                        allyWeightInLane += 6;
                    }
                    break;
                } else if (g2 != null)
                    break;
            }

            int currentDist = tactical.enemies().isEmpty() ? Integer.MAX_VALUE
                    : nearestManhattan(pos.row(), pos.col(), tactical.enemies());
            WinPathResult myTTWNow = estimateTurnsToRow0(board, piece, pos.row(), pos.col(), tactical.threats(),
                    actionsRemaining);

            for (Plot p : candidates) {
                int dr = p.getRow();
                int dc = p.getCol();
                if (isLethalThreatNextTurn(board, piece, dr, dc))
                    continue;

                int score = config.scoreManeuverBase();
                if (tactical.threats().isThreatened(dr, dc)) {
                    score -= (config.penaltyThreatBase()
                            + Math.min(15, 5 * Math.max(0, tactical.threats().getCount(dr, dc) - 1)));
                }

                if (dr < pos.row())
                    score += config.dirForwardBonus();
                else if (dr > pos.row())
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
                WinPathResult myTTWDest = estimateTurnsToRow0(board, piece, dr, dc, tactical.threats(),
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

            if (bestPlot != null && !(bestPlot.getRow() == pos.row() && bestPlot.getCol() == pos.col())) {
                final Plot finalManeuverPlot = bestPlot;
                output.add(new Intent(bestScore,
                        () -> moveAndVerify(board, pos.row(), pos.col(), finalManeuverPlot, piece,
                                finalManeuverPlot.getRow(), finalManeuverPlot.getCol()),
                        IntentType.MANEUVER));
            }
        }
    }

    private int calculateWinPathPenalty(Board board, MonsterGamePiece piece, WinPathResult result, ThreatMap threats) {
        int penalty = Math.min(config.penaltyWinPathExposureMax(),
                result.threatExposure() * config.penaltyWinPathExposureScale());
        if (result.turns() == 0)
            return penalty;

        if (isLethalThreatNextTurn(board, piece, result.firstMove().row(), result.firstMove().col())) {
            penalty += config.penaltyLethalExposure();
        }

        if (result.endsTurn0InThreat()) {
            int attackerCount = threats.getCount(result.firstMove().row(), result.firstMove().col());
            penalty += config.penaltyWinPathEndThreatBase() + Math.min(config.penaltyWinPathEndThreatExtraMax(),
                    config.penaltyWinPathEndThreatExtraScale() * Math.max(0, attackerCount - 1));
        }
        return penalty;
    }

    private WinPathResult estimateTurnsToRow0(Board board, MonsterGamePiece piece, int row, int col, ThreatMap threats,
            int actions) {
        int speed = piece.getEffectiveSpeed();
        int maxActions = piece.getEffectiveActions();
        Map<Coord, List<Coord>> reachCache = new HashMap<>();
        Deque<BotSearchState> queue = new ArrayDeque<>();
        Set<Integer> visited = new HashSet<>();

        Coord startPos = new Coord(row, col);
        BotSearchState start = new BotSearchState(startPos, 0, actions, 0, null, false);
        queue.add(start);
        visited.add(start.pack());

        int expansions = 0;
        while (!queue.isEmpty() && expansions < config.maxWinExpansions()) {
            BotSearchState state = queue.poll();
            if (state.pos.row() == 0) {
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
                List<Coord> neighbors = reachCache.computeIfAbsent(state.pos, p -> {
                    List<Coord> list = new ArrayList<>();
                    for (Plot plot : board.getReachablePlots(p.row(), p.col(), speed)) {
                        list.add(new Coord(plot.getRow(), plot.getCol()));
                    }
                    return list;
                });
                for (Coord next : neighbors) {
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

    private boolean wouldEnableLethal(Board board, MonsterGamePiece piece, int row, int col) {
        int[][] directions = new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int[] dir : directions) {
            int nr = row + dir[0];
            int nc = col + dir[1];
            if (inBounds(board, nr, nc)) {
                GamePiece gp = board.getGamePieceAtPos(nr, nc);
                if (gp instanceof MonsterGamePiece target && target.getAlignment() == PieceAlignment.P1) {
                    if (piece.getEffectiveDamage() >= target.getStats().getCurrentHealth())
                        return true;
                }
            }
        }
        return false;
    }

    private Plot getPlot(Board board, Coord coord) {
        Renderable r = board.getPlotAtPos(coord.row(), coord.col());
        return (r instanceof Plot p) ? p : null;
    }
}
