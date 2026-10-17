package io.github.elderpath_crusade.bot.eval;

import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.match.CardInstance;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.BoardGrid;
import io.github.elderpath_crusade.game.PlayerManager;
import io.github.elderpath_crusade.bot.eval.BotActionContext.Intent;
import io.github.elderpath_crusade.bot.eval.BotActionContext.IntentType;
import io.github.elderpath_crusade.bot.eval.BotActionContext.PieceEntry;
import io.github.elderpath_crusade.bot.eval.BotActionContext.TacticalState;
import io.github.elderpath_crusade.match.Coord;

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
    public void build(BoardGrid board, TacticalState tactical, List<Intent> output) {
        var player = GameContext.get().getPlayerManager().get(PieceAlignment.P2);
        if (player == null) {
            return;
        }

        int currentMana = player.getMana();
        int homeRow = board.getHomeRow(PieceAlignment.P2);
        int cols = board.getCols();

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
        List<Coord> candidates = new ArrayList<>();
        if (bestCol != -1 && board.isValidSummonTarget(homeRow, bestCol, PieceAlignment.P2)) {
            candidates.add(new Coord(homeRow, bestCol));
        }

        if (candidates.isEmpty()) {
            for (int col = 0; col < cols; col++) {
                if (board.isValidSummonTarget(homeRow, col, PieceAlignment.P2)) {
                    candidates.add(new Coord(homeRow, col));
                }
            }
        }

        if (candidates.isEmpty()) {
            return;
        }

        // Choose best affordable card based on value/cost heuristic
        CardInstance bestCard = null;
        int bestValueScore = Integer.MIN_VALUE;

        for (CardInstance card : player.hand.getCards()) {
            PieceDefinition def = CardCatalog.getPieceDefinition(card.cardName());
            if (def == null || def.cost() > currentMana) {
                continue;
            }

            int value = (def.health() * 2) + (def.damage() * 3) +
                    (def.actions()) + (def.speed()) + def.cost();

            if (value > bestValueScore) {
                bestValueScore = value;
                bestCard = card;
            }
        }

        if (bestCard != null) {
            Coord target = candidates.get(0);
            final CardInstance card = bestCard;
            int score = config.scoreSummonBase() + Math.min(30, bestValueScore / 2);

            output.add(new Intent(score,
                    () -> summonAndVerify(card, target),
                    IntentType.DEF_SUMMON));
        }
    }
}
