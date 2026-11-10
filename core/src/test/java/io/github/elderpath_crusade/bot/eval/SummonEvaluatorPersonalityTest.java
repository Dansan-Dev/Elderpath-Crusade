package io.github.elderpath_crusade.bot.eval;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.bot.eval.BotActionContext.Intent;
import io.github.elderpath_crusade.bot.eval.BotActionContext.PieceEntry;
import io.github.elderpath_crusade.bot.eval.BotActionContext.TacticalState;
import io.github.elderpath_crusade.bot.search.ThreatMap;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.ecs.factory.PieceFactory;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.BoardGrid;
import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.MatchConfig;
import io.github.elderpath_crusade.match.MatchSetup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BOT-005's formation (lane preference), grouping and saving dimensions, exercised directly
 * against {@link SummonEvaluator} with a real match (no threats on the board, so the only thing
 * driving the column choice/summon decision is the personality itself) — same bar as
 * {@code MatchSetupCardModificationTest}/{@code ElderPieceSpawnTest}: real engine, in-code
 * content, no assets needed.
 */
class SummonEvaluatorPersonalityTest {
    /** Same stats as the rest of the suite's "Wolf" — value ≈ 13, a realistic 1-cost unit. */
    private static final PieceDefinition WOLF = new PieceDefinition("Wolf", 1, 3, 1, 2, 1, List.of());
    /** Deliberately weak — value ≈ 2, well under even a modest saving threshold. */
    private static final PieceDefinition WEAKLING = new PieceDefinition("Weakling", 0, 1, 0, 0, 1, List.of());

    private BoardGrid grid;

    @BeforeEach
    void setUp() {
        GameContext.createHeadless();
        PieceRegistry.register(WOLF);
        PieceRegistry.register(WEAKLING);
        CardCatalog.clear();
        CardCatalog.registerSummon("Wolf", "Wolf");
        CardCatalog.registerSummon("Weakling", "Weakling");
    }

    @AfterEach
    void tearDown() {
        CardCatalog.clear();
    }

    private void prepareNoThreatMatch(String card) {
        MatchConfig config = MatchConfig.standard(GameMode.DEMO,
                List.of(card, card, card, card), List.of(card, card, card, card), 1L, Map.of());
        grid = MatchSetup.prepare(config);
        MatchSetup.begin();
        GameContext.get().getPlayerManager().get(PieceAlignment.P1).setMana(10);
    }

    /** Places an existing ally (not drawn from hand) on the home row — for formation/grouping scenarios. */
    private void placeExistingAllyAt(int col) {
        int homeRow = grid.getHomeRow(PieceAlignment.P1);
        Entity ally = PieceFactory.createPiece(WOLF, PieceAlignment.P1, homeRow, col);
        grid.addEntity(homeRow, col, ally);
    }

    private List<PieceEntry> existingAllies() {
        List<PieceEntry> allies = new ArrayList<>();
        int homeRow = grid.getHomeRow(PieceAlignment.P1);
        for (int col = 0; col < grid.getCols(); col++) {
            Entity e = grid.getEntityAt(homeRow, col);
            if (e != null) allies.add(new PieceEntry(new Coord(homeRow, col), e));
        }
        return allies;
    }

    /** Runs SummonEvaluator in isolation (no enemy threats) and, if it proposes anything, executes it. */
    private List<Intent> summonIntents(BotConfig config) {
        TacticalState tactical = new TacticalState(PieceAlignment.P1, existingAllies(), List.of(),
                new ThreatMap(grid.getRows(), grid.getCols()));
        List<Intent> intents = new ArrayList<>();
        new SummonEvaluator(config).build(grid, tactical, intents);
        return intents;
    }

    /** Executes the proposed summon and returns the column it landed on, by diffing occupancy before/after. */
    private int executeAndFindSummonedColumn(BotConfig config) {
        int homeRow = grid.getHomeRow(PieceAlignment.P1);
        boolean[] occupiedBefore = new boolean[grid.getCols()];
        for (int col = 0; col < grid.getCols(); col++) occupiedBefore[col] = grid.isOccupied(homeRow, col);

        List<Intent> intents = summonIntents(config);
        assertFalse(intents.isEmpty(), "expected a summon intent");
        assertTrue(intents.get(0).execute().get(), "the summon should succeed");

        for (int col = 0; col < grid.getCols(); col++) {
            if (!occupiedBefore[col] && grid.isOccupied(homeRow, col)) return col;
        }
        throw new AssertionError("nothing new landed on the home row");
    }

    // --- formation (lanePreference) ---

    @Test
    void noLanePreference_summonsAtTheLowestValidColumn_unchangedFromBeforeBot005() {
        prepareNoThreatMatch("Wolf");
        assertEquals(0, executeAndFindSummonedColumn(BotConfig.defaultConfig()));
    }

    @Test
    void positiveLanePreference_summonsAtTheCenterColumn() {
        prepareNoThreatMatch("Wolf");
        BotConfig centerRush = BotConfig.forPersonality(new BotPersonality(0, 0, 0, 0, 1, 0, 0, 0));
        assertEquals(2, executeAndFindSummonedColumn(centerRush), "the standard board is 5 columns wide — column 2 is dead center");
    }

    @Test
    void negativeLanePreference_summonsAtAFlankColumn() {
        prepareNoThreatMatch("Wolf");
        BotConfig flanker = BotConfig.forPersonality(new BotPersonality(0, 0, 0, 0, -1, 0, 0, 0));
        int col = executeAndFindSummonedColumn(flanker);
        assertTrue(col == 0 || col == 4, "expected an edge column (0 or 4), got " + col);
    }

    // --- grouping ---

    @Test
    void noGroupingBias_ignoresExistingAlliesEntirely_unchangedFromBeforeBot005() {
        prepareNoThreatMatch("Wolf");
        placeExistingAllyAt(2); // center — would otherwise pull a grouping-biased choice
        assertEquals(0, executeAndFindSummonedColumn(BotConfig.defaultConfig()),
                "with no grouping preference, the lowest valid column wins regardless of where allies are");
    }

    @Test
    void positiveGrouping_summonsNextToTheExistingAlly() {
        prepareNoThreatMatch("Wolf");
        placeExistingAllyAt(2);
        BotConfig clusterer = BotConfig.forPersonality(new BotPersonality(0, 0, 0, 0, 0, 1, 0, 0));
        int col = executeAndFindSummonedColumn(clusterer);
        assertEquals(1, col, "closest available column to the ally at 2 is 1 (3 is equidistant but 1 has the lower index)");
    }

    @Test
    void negativeGrouping_summonsFartherFromTheExistingAllyThanPositiveGroupingDoes() {
        prepareNoThreatMatch("Wolf");
        placeExistingAllyAt(2);
        int closeCol = executeAndFindSummonedColumn(BotConfig.forPersonality(new BotPersonality(0, 0, 0, 0, 0, 1, 0, 0)));

        prepareNoThreatMatch("Wolf");
        placeExistingAllyAt(2);
        int farCol = executeAndFindSummonedColumn(BotConfig.forPersonality(new BotPersonality(0, 0, 0, 0, 0, -1, 0, 0)));

        assertTrue(Math.abs(farCol - 2) > Math.abs(closeCol - 2),
                "a spreading bot should land farther from the existing ally than a clustering one: close=" + closeCol + " far=" + farCol);
    }

    @Test
    void positiveGrouping_withNoExistingAllies_fallsBackToTheLowestValidColumn() {
        prepareNoThreatMatch("Wolf"); // no allies placed — nothing to group with
        BotConfig clusterer = BotConfig.forPersonality(new BotPersonality(0, 0, 0, 0, 0, 1, 0, 0));
        assertEquals(0, executeAndFindSummonedColumn(clusterer), "with nothing to cluster around, this degrades to the plain lowest-index pick");
    }

    // --- saving ---

    @Test
    void noSavingBias_stillSummonsAWeakCard_unchangedFromBeforeBot005() {
        prepareNoThreatMatch("Weakling");
        assertFalse(summonIntents(BotConfig.defaultConfig()).isEmpty());
    }

    @Test
    void highSaving_declinesToSummonAWeakCard() {
        prepareNoThreatMatch("Weakling");
        BotConfig saver = BotConfig.forPersonality(new BotPersonality(0, 0, 0, 1, 0, 0, 0, 0));
        assertTrue(summonIntents(saver).isEmpty(), "a maximally saving bot should hold its mana rather than play a near-worthless card");
    }

    @Test
    void highSaving_alsoDeclinesARealisticOneCostUnit() {
        // Proves the threshold is calibrated against real (not just contrived) card values.
        prepareNoThreatMatch("Wolf");
        BotConfig saver = BotConfig.forPersonality(new BotPersonality(0, 0, 0, 1, 0, 0, 0, 0));
        assertTrue(summonIntents(saver).isEmpty(), "a maximally saving bot should pass on a modest 1-cost unit too");
    }

    @Test
    void negativeSaving_stillSummonsAndScoresHigherThanBalanced() {
        prepareNoThreatMatch("Wolf");
        List<Intent> balanced = summonIntents(BotConfig.defaultConfig());
        List<Intent> spender = summonIntents(BotConfig.forPersonality(new BotPersonality(0, 0, 0, -1, 0, 0, 0, 0)));
        assertFalse(balanced.isEmpty());
        assertFalse(spender.isEmpty());
        assertTrue(spender.get(0).score() > balanced.get(0).score(), "an eager spender should rate playing a card more highly");
    }
}
