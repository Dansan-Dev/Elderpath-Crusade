package io.github.elderpath_crusade.bot.impl;

import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.match.CardInstance;import io.github.elderpath_crusade.game.PlayerManager;

import io.github.elderpath_crusade.GameContext;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.BoardGrid;
import io.github.elderpath_crusade.bot.Bot;
import io.github.elderpath_crusade.bot.eval.BotActionContext;
import io.github.elderpath_crusade.bot.eval.BotConfig;
import io.github.elderpath_crusade.bot.eval.BotUtils;
import io.github.elderpath_crusade.bot.eval.IntentGenerator;
import io.github.elderpath_crusade.bot.eval.AttackEvaluator;
import io.github.elderpath_crusade.bot.eval.MovementEvaluator;
import io.github.elderpath_crusade.bot.eval.SummonEvaluator;
import io.github.elderpath_crusade.match.Coord;
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
    private PieceAlignment self = PieceAlignment.P2;

    @Override
    public String getName() {
        return "SmartBot";
    }

    @Override
    public void onTurnStarted(PieceAlignment player) {
        if (player != PieceAlignment.P1 && player != PieceAlignment.P2)
            return;
        this.self = player;
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
        if (GameContext.get().getTurnManager().getCurrentPlayer() != self)
            return;
        if (stepsDone >= MAX_STEPS) {
            Logger.log("[SmartBot]", "Reached step cap");
            endTurn();
            return;
        }
        BoardGrid board = GameContext.get().getActiveGrid();
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
        GameContext.get().getMatchClock().schedule(STEP_DELAY, () -> step(stepsDone));
    }

    private void endTurn() {
        if (ended)
            return;
        ended = true;
        turnActive = false;
        PieceAlignment endingFor = self;
        GameContext.get().getMatchClock().schedule(END_DELAY, () -> {
            if (!GameContext.get().getGameManager().isPaused()
                    && GameContext.get().getTurnManager().getCurrentPlayer() == endingFor)
                GameContext.get().getTurnManager().endTurn();
        });
    }

    private boolean executeBestIntent(BoardGrid board) {
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

    private BotActionContext.TacticalState buildTacticalState(BoardGrid board) {
        List<BotActionContext.PieceEntry> allies = new ArrayList<>();
        List<BotActionContext.PieceEntry> enemies = new ArrayList<>();
        int rows = board.getRows();
        int cols = board.getCols();
        PieceAlignment enemy = BotActionContext.opponentOf(self);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                Entity entity = board.getEntityAt(row, col);
                if (entity == null)
                    continue;

                Coord pos = new Coord(row, col);
                PieceAlignment alignment = EntityUtils.getAlignment(entity);
                if (alignment == self) allies.add(new BotActionContext.PieceEntry(pos, entity));
                else if (alignment == enemy) enemies.add(new BotActionContext.PieceEntry(pos, entity));
            }
        }

        ThreatMap threats = BotUtils.computeThreatMap(board, enemy);
        return new BotActionContext.TacticalState(self, allies, enemies, threats);
    }

    private boolean shouldEndNow(BoardGrid board) {
        PlayerManager.PlayerState player = GameContext.get().getPlayerManager().get(self);
        if (player == null)
            return true;

        for (int row = 0; row < board.getRows(); row++) {
            for (int col = 0; col < board.getCols(); col++) {
                Entity entity = board.getEntityAt(row, col);
                if (entity == null || EntityUtils.getAlignment(entity) != self)
                    continue;
                if (EntityUtils.isStunned(entity) || EntityUtils.getRemainingActions(entity) <= 0)
                    continue;
                return false;
            }
        }

        for (CardInstance card : player.hand.getCards()) {
            PieceDefinition def = CardCatalog.getPieceDefinition(card.cardName());
            if (def == null || def.cost() > player.getMana())
                continue;
            return false;
        }

        return true;
    }
}
