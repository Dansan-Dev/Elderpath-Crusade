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

import java.util.List;

/**
 * Generates and scores intent for summoning new units defensively.
 */
public class SummonEvaluator extends BotEvaluatorBase {

    public SummonEvaluator(BotConfig config) {
        super(config);
    }

    /** How strongly {@code lanePreference} can pull the chosen column away from the threat column, in score points — kept well below THREAT_COLUMN_SCORE so formation never overrides an urgent defensive placement. */
    private static final double LANE_SCALE = 4;
    private static final int THREAT_COLUMN_SCORE = 1000;

    @Override
    public void build(BoardGrid board, TacticalState tactical, List<Intent> output) {
        PieceAlignment self = tactical.self();
        var player = GameContext.get().getPlayerManager().get(self);
        if (player == null) {
            return;
        }

        int currentMana = player.getMana();
        Coord target = chooseSummonColumn(board, self, tactical);
        if (target == null) {
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

            // BOT-005 saving dimension: 0 means no threshold at all (every affordable card still
            // qualifies, exactly as before). A positive savingBias raises the bar, so a saving
            // bot skips a merely-OK card and holds its mana for something better (or just banks
            // it) rather than always dumping whatever it can afford. The scale (18 at max
            // saving) is picked against this module's own in-code test stats (e.g. a "Wolf"-ish
            // 1-cost unit scores ~13) — real balance against the actual roster needs the user's
            // own local tuning, same caveat as EnemyDeckCatalog's.
            int minValue = savingBias() > 0 ? Math.round(savingBias() * 18) : 0;
            if (value < minValue) {
                continue;
            }

            if (value > bestValueScore) {
                bestValueScore = value;
                bestCard = card;
            }
        }

        if (bestCard != null) {
            final CardInstance card = bestCard;
            int score = config.scoreSummonBase() + Math.min(30, bestValueScore / 2);
            // A negative savingBias (spend eagerly) nudges this intent's score up so it wins
            // ties against holding position more often; 0 contributes nothing.
            if (savingBias() < 0) {
                score += Math.round(-savingBias() * 8);
            }

            output.add(new Intent(score,
                    () -> summonAndVerify(self, card, target),
                    IntentType.DEF_SUMMON));
        }
    }

    private float savingBias() {
        return config.savingBias();
    }

    /**
     * The defensive threat column (closest enemy to our home row) always wins when it's a valid
     * summon target — {@code lanePreference} (BOT-005 formation) only breaks ties among the
     * remaining columns when there's no specific threat to answer, nudging toward the center
     * (+1) or the flanks (-1). At {@code lanePreference == 0} every column scores identically in
     * that case, and the lowest-index valid column wins (the strict {@code >} comparison never
     * replaces an equal score) — exactly {@code candidates.get(0)}'s old behavior.
     */
    private Coord chooseSummonColumn(BoardGrid board, PieceAlignment self, TacticalState tactical) {
        int homeRow = board.getHomeRow(self);
        int cols = board.getCols();
        double center = (cols - 1) / 2.0;

        int threatCol = -1;
        int bestDist = Integer.MAX_VALUE;
        for (PieceEntry entry : tactical.enemies()) {
            Coord pos = entry.pos();
            int dist = Math.abs(homeRow - pos.row());
            if (dist < bestDist) {
                bestDist = dist;
                threatCol = pos.col();
            }
        }

        Coord best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int col = 0; col < cols; col++) {
            if (!board.isValidSummonTarget(homeRow, col, self)) continue;
            double score = (col == threatCol) ? THREAT_COLUMN_SCORE : 0;
            double centerness = -Math.abs(col - center);
            score += config.lanePreference() * centerness * LANE_SCALE;
            if (score > bestScore) {
                bestScore = score;
                best = new Coord(homeRow, col);
            }
        }
        return best;
    }
}
