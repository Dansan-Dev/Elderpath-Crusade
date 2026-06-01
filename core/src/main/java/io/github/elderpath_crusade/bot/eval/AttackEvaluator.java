package io.github.elderpath_crusade.bot.eval;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.bot.command.AttackCommand;
import io.github.elderpath_crusade.bot.eval.BotActionContext.Intent;
import io.github.elderpath_crusade.bot.eval.BotActionContext.IntentType;
import io.github.elderpath_crusade.bot.eval.BotActionContext.PieceEntry;
import io.github.elderpath_crusade.bot.eval.BotActionContext.TacticalState;
import io.github.elderpath_crusade.bot.search.Coord;
import io.github.elderpath_crusade.bot.search.ThreatMap;

import java.util.List;

/**
 * Generates and scores intent for adjacent attacks.
 */
public class AttackEvaluator extends BotEvaluatorBase {

    public AttackEvaluator(BotConfig config) {
        super(config);
    }

    @Override
    public void build(Board board, TacticalState tactical, List<Intent> output) {
        for (PieceEntry entry : tactical.allies()) {
            MonsterGamePiece attacker = entry.piece();
            Coord source = entry.pos();

            if (!canAct(attacker)) {
                continue;
            }

            List<Plot> hostilePlots = board.getAttackableEnemyPlots(source.row(), source.col(), PieceAlignment.P2);
            if (hostilePlots == null) {
                continue;
            }

            for (Plot plot : hostilePlots) {
                Coord target = new Coord(plot.getRow(), plot.getCol());
                GamePiece defender = board.getGamePieceAtPos(target.row(), target.col());

                int score = scoreAdjacentAttack(board, source, target, attacker, defender, tactical.threats());
                output.add(new Intent(score,
                        () -> attackAndVerify(board, source.row(), source.col(), target.row(), target.col(), attacker),
                        IntentType.ADJ_ATTACK,
                        new AttackCommand(attacker.getId().toString(), source.row(), source.col(), target.row(), target.col())));
            }
        }
    }

    private int scoreAdjacentAttack(Board board, Coord source, Coord target, MonsterGamePiece attacker,
            GamePiece defender, ThreatMap threats) {
        int basePoints = config.scoreAdjAttackBase();
        boolean lethal = false;

        if (defender instanceof MonsterGamePiece mgpDefender) {
            int damage = attacker.getEffectiveDamage();
            int health = mgpDefender.getStats().getCurrentHealth();
            lethal = damage >= health;

            if (lethal) {
                basePoints = config.scoreAdjAttackLethal();
            }

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
        boolean lethalHere = isLethalThreatNextTurn(board, attacker, source.row(), source.col());
        if (lethalHere && actionsBefore <= 1) {
            basePoints -= config.penaltyThreatBase();
        }

        if (!lethalHere && !lethal && threats.isThreatened(source.row(), source.col())) {
            int threatenedCount = threats.getCount(source.row(), source.col());
            // Value at Risk: Higher value units are more cautious
            int myValueFactor = Math.min(10, attacker.getStats().getCost()) / 3;
            int penalty = 8 + Math.min(12, (3 + myValueFactor) * Math.max(0, threatenedCount - 1));
            if (actionsBefore <= 1) {
                basePoints -= penalty;
            } else {
                basePoints -= (penalty / 2);
            }
        }

        return basePoints;
    }
}
