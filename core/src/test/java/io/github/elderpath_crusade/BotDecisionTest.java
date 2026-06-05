package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.bot.eval.AttackEvaluator;
import io.github.elderpath_crusade.bot.eval.BotActionContext.Intent;
import io.github.elderpath_crusade.bot.eval.BotActionContext.IntentType;
import io.github.elderpath_crusade.bot.eval.BotActionContext.PieceEntry;
import io.github.elderpath_crusade.bot.eval.BotActionContext.TacticalState;
import io.github.elderpath_crusade.bot.eval.BotConfig;
import io.github.elderpath_crusade.bot.search.Coord;
import io.github.elderpath_crusade.bot.search.ThreatMap;
import io.github.elderpath_crusade.ecs.components.*;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.Plot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BotDecisionTest {

    private BotConfig config;
    private AttackEvaluator evaluator;
    private Board board;
    private ThreatMap threats;
    private Engine engine;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        config = BotConfig.defaultConfig();
        evaluator = new AttackEvaluator(config);
        board = mock(Board.class);
        when(board.getROWS()).thenReturn(3);
        when(board.getCOLS()).thenReturn(3);
        threats = mock(ThreatMap.class);
        engine = new Engine();
    }

    private Entity makeEntity(PieceAlignment alignment, int damage, int speed, int actions, int cost, int health) {
        Entity entity = engine.createEntity();
        StatsComponent stats = new StatsComponent().set(cost, health, damage, speed, actions);
        stats.currentHealth = health;
        stats.remainingActions = actions;
        entity.add(stats);
        ComputedStatsComponent computed = new ComputedStatsComponent();
        computed.damage = damage;
        computed.speed = speed;
        computed.actions = actions;
        computed.maxHealth = health;
        computed.cost = cost;
        entity.add(computed);
        AlignmentComponent ac = new AlignmentComponent().set(alignment);
        entity.add(ac);
        IdentityComponent ic = new IdentityComponent().set("TestPiece");
        entity.add(ic);
        engine.addEntity(entity);
        return entity;
    }

    private Entity mockAlly(int actions, int damage) {
        return makeEntity(PieceAlignment.P2, damage, 1, actions, 3, 5);
    }

    private Entity mockEnemy(int health) {
        return makeEntity(PieceAlignment.P1, 2, 1, 1, 3, health);
    }

    private Plot mockPlot(int row, int col) {
        Plot plot = mock(Plot.class);
        when(plot.getRow()).thenReturn(row);
        when(plot.getCol()).thenReturn(col);
        return plot;
    }

    @Test
    void attackEvaluator_generatesIntentForAdjacentEnemy() {
        Entity ally = mockAlly(1, 3);
        Entity enemy = mockEnemy(5);
        Plot targetPlot = mockPlot(1, 2);

        when(board.getAttackableEnemyPlots(1, 1, PieceAlignment.P2)).thenReturn(List.of(targetPlot));
        when(board.getEntityAtPos(1, 2)).thenReturn(enemy);

        TacticalState tactical = new TacticalState(
                List.of(new PieceEntry(new Coord(1, 1), ally)),
                List.of(),
                threats);

        List<Intent> output = new ArrayList<>();
        evaluator.build(board, tactical, output);

        assertEquals(1, output.size());
        assertEquals(IntentType.ADJ_ATTACK, output.get(0).kind());
        assertTrue(output.get(0).score() >= config.scoreAdjAttackBase());
    }

    @Test
    void attackEvaluator_lethalAttackScoresHigher() {
        Entity ally = mockAlly(1, 3);
        Entity enemy = mockEnemy(2);
        Plot targetPlot = mockPlot(1, 2);

        when(board.getAttackableEnemyPlots(1, 1, PieceAlignment.P2)).thenReturn(List.of(targetPlot));
        when(board.getEntityAtPos(1, 2)).thenReturn(enemy);

        TacticalState tactical = new TacticalState(
                List.of(new PieceEntry(new Coord(1, 1), ally)),
                List.of(),
                threats);

        List<Intent> output = new ArrayList<>();
        evaluator.build(board, tactical, output);

        assertEquals(1, output.size());
        assertTrue(output.get(0).score() >= config.scoreAdjAttackLethal());
    }

    @Test
    void attackEvaluator_skipsStunnedPiece() {
        Entity ally = mockAlly(1, 3);
        StunComponent stun = new StunComponent();
        stun.turnsRemaining = 1;
        ally.add(stun);

        TacticalState tactical = new TacticalState(
                List.of(new PieceEntry(new Coord(1, 1), ally)),
                List.of(),
                threats);

        List<Intent> output = new ArrayList<>();
        evaluator.build(board, tactical, output);

        assertTrue(output.isEmpty());
    }

    @Test
    void attackEvaluator_skipsNoActionsPiece() {
        Entity ally = mockAlly(0, 3);

        TacticalState tactical = new TacticalState(
                List.of(new PieceEntry(new Coord(1, 1), ally)),
                List.of(),
                threats);

        List<Intent> output = new ArrayList<>();
        evaluator.build(board, tactical, output);

        assertTrue(output.isEmpty());
    }

    @Test
    void attackEvaluator_noTargets_emptyOutput() {
        Entity ally = mockAlly(1, 3);
        when(board.getAttackableEnemyPlots(1, 1, PieceAlignment.P2)).thenReturn(List.of());

        TacticalState tactical = new TacticalState(
                List.of(new PieceEntry(new Coord(1, 1), ally)),
                List.of(),
                threats);

        List<Intent> output = new ArrayList<>();
        evaluator.build(board, tactical, output);

        assertTrue(output.isEmpty());
    }

    @Test
    void attackEvaluator_multipleTargets_generatesMultipleIntents() {
        Entity ally = mockAlly(1, 3);
        Entity enemy1 = mockEnemy(5);
        Entity enemy2 = mockEnemy(4);
        Plot plot1 = mockPlot(1, 2);
        Plot plot2 = mockPlot(0, 1);

        when(board.getAttackableEnemyPlots(1, 1, PieceAlignment.P2)).thenReturn(List.of(plot1, plot2));
        when(board.getEntityAtPos(1, 2)).thenReturn(enemy1);
        when(board.getEntityAtPos(0, 1)).thenReturn(enemy2);

        TacticalState tactical = new TacticalState(
                List.of(new PieceEntry(new Coord(1, 1), ally)),
                List.of(),
                threats);

        List<Intent> output = new ArrayList<>();
        evaluator.build(board, tactical, output);

        assertEquals(2, output.size());
        assertTrue(output.stream().allMatch(i -> i.kind() == IntentType.ADJ_ATTACK));
    }
}
