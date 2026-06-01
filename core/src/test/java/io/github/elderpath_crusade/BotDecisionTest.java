package io.github.elderpath_crusade;

import io.github.elderpath_crusade.bot.eval.AttackEvaluator;
import io.github.elderpath_crusade.bot.eval.BotActionContext.Intent;
import io.github.elderpath_crusade.bot.eval.BotActionContext.IntentType;
import io.github.elderpath_crusade.bot.eval.BotActionContext.PieceEntry;
import io.github.elderpath_crusade.bot.eval.BotActionContext.TacticalState;
import io.github.elderpath_crusade.bot.eval.BotConfig;
import io.github.elderpath_crusade.bot.search.Coord;
import io.github.elderpath_crusade.bot.search.ThreatMap;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
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
    }

    private MonsterGamePiece mockAlly(int actions, int damage) {
        MonsterGamePiece ally = mock(MonsterGamePiece.class);
        when(ally.getAlignment()).thenReturn(PieceAlignment.P2);
        when(ally.isStunned()).thenReturn(false);
        GamePieceStats stats = mock(GamePieceStats.class);
        when(stats.getRemainingActions()).thenReturn(actions);
        when(stats.getCost()).thenReturn(3);
        when(stats.getCurrentHealth()).thenReturn(5);
        when(stats.getSpeed()).thenReturn(1);
        when(ally.getStats()).thenReturn(stats);
        when(ally.getEffectiveDamage()).thenReturn(damage);
        when(ally.getEffectiveActions()).thenReturn(actions);
        when(ally.getAbilities()).thenReturn(List.of());
        return ally;
    }

    private MonsterGamePiece mockEnemy(int health) {
        MonsterGamePiece enemy = mock(MonsterGamePiece.class);
        when(enemy.getAlignment()).thenReturn(PieceAlignment.P1);
        GamePieceStats stats = mock(GamePieceStats.class);
        when(stats.getCurrentHealth()).thenReturn(health);
        when(stats.getCost()).thenReturn(3);
        when(enemy.getStats()).thenReturn(stats);
        when(enemy.getEffectiveDamage()).thenReturn(2);
        when(enemy.getEffectiveActions()).thenReturn(1);
        return enemy;
    }

    private Plot mockPlot(int row, int col) {
        Plot plot = mock(Plot.class);
        when(plot.getRow()).thenReturn(row);
        when(plot.getCol()).thenReturn(col);
        return plot;
    }

    @Test
    void attackEvaluator_generatesIntentForAdjacentEnemy() {
        MonsterGamePiece ally = mockAlly(1, 3);
        MonsterGamePiece enemy = mockEnemy(5);
        Plot targetPlot = mockPlot(1, 2);

        when(board.getAttackableEnemyPlots(1, 1, PieceAlignment.P2)).thenReturn(List.of(targetPlot));
        when(board.getGamePieceAtPos(1, 2)).thenReturn(enemy);

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
        MonsterGamePiece ally = mockAlly(1, 3);
        MonsterGamePiece enemy = mockEnemy(2);
        Plot targetPlot = mockPlot(1, 2);

        when(board.getAttackableEnemyPlots(1, 1, PieceAlignment.P2)).thenReturn(List.of(targetPlot));
        when(board.getGamePieceAtPos(1, 2)).thenReturn(enemy);

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
        MonsterGamePiece ally = mockAlly(1, 3);
        when(ally.isStunned()).thenReturn(true);

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
        MonsterGamePiece ally = mockAlly(0, 3);

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
        MonsterGamePiece ally = mockAlly(1, 3);
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
        MonsterGamePiece ally = mockAlly(1, 3);
        MonsterGamePiece enemy1 = mockEnemy(5);
        MonsterGamePiece enemy2 = mockEnemy(4);
        Plot plot1 = mockPlot(1, 2);
        Plot plot2 = mockPlot(0, 1);

        when(board.getAttackableEnemyPlots(1, 1, PieceAlignment.P2)).thenReturn(List.of(plot1, plot2));
        when(board.getGamePieceAtPos(1, 2)).thenReturn(enemy1);
        when(board.getGamePieceAtPos(0, 1)).thenReturn(enemy2);

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
