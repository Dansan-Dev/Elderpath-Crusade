package io.github.elderpath_crusade.bot.impl;

import io.github.elderpath_crusade.GameContext;
import com.badlogic.gdx.utils.Timer;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.game_objects.cards.Card;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;
import io.github.elderpath_crusade.managers.*;
import io.github.elderpath_crusade.bot.Bot;
import io.github.elderpath_crusade.bot.eval.BotActionContext;
import io.github.elderpath_crusade.bot.eval.BotConfig;
import io.github.elderpath_crusade.bot.eval.BotUtils;
import io.github.elderpath_crusade.bot.eval.IntentGenerator;
import io.github.elderpath_crusade.bot.eval.AttackEvaluator;
import io.github.elderpath_crusade.bot.eval.MovementEvaluator;
import io.github.elderpath_crusade.bot.eval.SummonEvaluator;
import io.github.elderpath_crusade.bot.search.Coord;
import io.github.elderpath_crusade.bot.search.ThreatMap;
import io.github.elderpath_crusade.utils.Logger;

import java.util.*;
import java.util.function.Supplier;

/**
 * Smarter bot implementing an urgency-driven policy via modular intent
 * evaluation:
 * - AttackEvaluator: Adjacent attacks (lethal/cost-weighted)
 * - MovementEvaluator: Win-path discovery, advancement, and tactical maneuver.
 * - SummonEvaluator: Defensive unit placement.
 */
public class SmartBot implements Bot {
    private static final float STEP_DELAY = 0.35f;
    private static final float END_DELAY = 0.4f;
    private static final int MAX_STEPS = 60;

    private final List<IntentGenerator> evaluators;

    public SmartBot() {
        this(BotConfig.defaultConfig());
    }

    public SmartBot(BotConfig config) {
        this.evaluators = List.of(
                new AttackEvaluator(config),
                new MovementEvaluator(config),
                new SummonEvaluator(config));
    }

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
        if (GameContext.get().getGameManager().isPaused())
            return;
        if (GameContext.get().getTurnManager().getCurrentPlayer() != PieceAlignment.P2)
            return;
        if (stepsDone >= MAX_STEPS) {
            Logger.log("[SmartBot]", "Reached step cap");
            endTurn();
            return;
        }
        Board board = GameContext.get().getActiveBoard();
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
                if (!GameContext.get().getGameManager().isPaused())
                    GameContext.get().getTurnManager().endTurn();
            }
        }, END_DELAY);
    }

    private boolean executeBestIntent(Board board) {
        BotActionContext.TacticalState tactical = buildTacticalState(board);
        List<BotActionContext.Intent> intents = new ArrayList<>();

        for (IntentGenerator evaluator : evaluators) {
            evaluator.build(board, tactical, intents);
        }

        if (intents.isEmpty()) {
            return false;
        }

        // Sort by score descending
        intents.sort(
                Comparator
                        .comparingInt(BotActionContext.Intent::score)
                        .reversed());

        for (BotActionContext.Intent intent : intents) {
            Supplier<Boolean> exec = intent.execute();
            if (exec != null && exec.get()) {
                Logger.log("[SmartBot]", "Intent executed: " + intent.kind() + " (score=" + intent.score() + ")");
                return true;
            }
        }
        return false;
    }

    private BotActionContext.TacticalState buildTacticalState(Board board) {
        List<BotActionContext.PieceEntry> allies = new ArrayList<>();
        List<BotActionContext.PieceEntry> enemies = new ArrayList<>();
        int rows = board.getROWS();
        int cols = board.getCOLS();

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                GamePiece gp = board.getGamePieceAtPos(row, col);
                if (!(gp instanceof MonsterGamePiece mgp))
                    continue;

                Coord pos = new Coord(row, col);
                switch (mgp.getAlignment()) {
                    case P1 -> enemies.add(new BotActionContext.PieceEntry(pos, mgp));
                    case P2 -> allies.add(new BotActionContext.PieceEntry(pos, mgp));
                    case NEUTRAL -> {}
                }

            }
        }

        ThreatMap threats = BotUtils.computeThreatMap(board, PieceAlignment.P1);
        return new BotActionContext.TacticalState(allies, enemies, threats);
    }

    private boolean shouldEndNow(Board board) {
        PlayerManager.PlayerState player = GameContext.get().getPlayerManager().get(PieceAlignment.P2);
        if (player == null)
            return true;

        for (int row = 0; row < board.getROWS(); row++) {
            for (int col = 0; col < board.getCOLS(); col++) {
                GamePiece gp = board.getGamePieceAtPos(row, col);
                if (!(gp instanceof MonsterGamePiece mgp) || mgp.getAlignment() != PieceAlignment.P2)
                    continue;
                if (mgp.isStunned() || mgp.getStats().getRemainingActions() <= 0)
                    continue;
                return false;
            }
        }

        if (player.hand == null)
            return true;

        for (Card card : player.hand.getCards()) {
            if (!(card instanceof SummonCard summonCard))
                continue;
            if (summonCard.getStats().getCost() > player.mana)
                continue;
            return false;
        }

        return true;
    }
}
