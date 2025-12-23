package io.github.elderpath_crusade.bot.eval;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;
import io.github.elderpath_crusade.game_objects.cards.Card;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.managers.PlayerManager;
import io.github.elderpath_crusade.bot.eval.BotActionContext.Intent;
import io.github.elderpath_crusade.bot.eval.BotActionContext.IntentType;
import io.github.elderpath_crusade.bot.eval.BotActionContext.PieceEntry;
import io.github.elderpath_crusade.bot.eval.BotActionContext.TacticalState;
import io.github.elderpath_crusade.bot.search.Coord;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates and scores intent for summoning new units defensively.
 */
public class SummonEvaluator extends BotEvaluatorBase {

    public SummonEvaluator(BotConfig config) {
        super(config);
    }

    @Override
    public void build(Board board, TacticalState tactical, List<Intent> output) {
        var player = PlayerManager.get(PieceAlignment.P2);
        if (player == null || player.hand == null) {
            return;
        }

        int currentMana = player.mana;
        int homeRow = board.getROWS() - 1;
        int cols = board.getCOLS();

        // Determine threatened column closest to our home row
        int bestCol = -1;
        int bestDist = Integer.MAX_VALUE;
        for (PieceEntry entry : tactical.enemies()) {
            Coord pos = entry.pos();
            int dist = Math.abs(homeRow - pos.row());
            if (dist < bestDist) {
                bestDist = dist;
                bestCol = pos.col();
            }
        }

        // Collect candidate plots: preferred column first, then all home-row plots
        List<Plot> candidates = new ArrayList<>();
        if (bestCol != -1) {
            Renderable r = board.getPlotAtPos(homeRow, bestCol);
            if (r instanceof Plot plot && board.isValidSummonTarget(plot, PieceAlignment.P2)) {
                candidates.add(plot);
            }
        }

        if (candidates.isEmpty()) {
            for (int col = 0; col < cols; col++) {
                Renderable r = board.getPlotAtPos(homeRow, col);
                if (r instanceof Plot plot && board.isValidSummonTarget(plot, PieceAlignment.P2)) {
                    candidates.add(plot);
                }
            }
        }

        if (candidates.isEmpty()) {
            return;
        }

        // Choose best affordable card based on value/cost heuristic
        SummonCard bestCard = null;
        int bestValueScore = Integer.MIN_VALUE;

        for (Card card : player.hand.getCards()) {
            if (!(card instanceof SummonCard sc) || sc.getStats().getCost() > currentMana) {
                continue;
            }

            var stats = sc.getStats();
            int value = (stats.getMaxHealth() * 2) + (stats.getDamage() * 3) +
                    (stats.getActions()) + (stats.getSpeed()) + stats.getCost();

            if (value > bestValueScore) {
                bestValueScore = value;
                bestCard = sc;
            }
        }

        if (bestCard != null) {
            Plot target = candidates.get(0);
            final SummonCard sc = bestCard;
            int score = config.scoreSummonBase() + Math.min(30, bestValueScore / 2);

            output.add(new Intent(score,
                    () -> summonAndVerify(board, sc, target),
                    IntentType.DEF_SUMMON));
        }
    }
}
