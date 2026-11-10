package io.github.elderpath_crusade.bot.eval;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.bot.command.AttackCommand;
import io.github.elderpath_crusade.bot.eval.BotActionContext.Intent;
import io.github.elderpath_crusade.bot.eval.BotActionContext.PieceEntry;
import io.github.elderpath_crusade.bot.eval.BotActionContext.TacticalState;
import io.github.elderpath_crusade.bot.search.ThreatMap;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
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
 * BOT-005's targeting dimension, exercised directly against {@link AttackEvaluator} — one
 * attacker adjacent to two different enemies (a big, dangerous one and a weak, easy-to-finish
 * one), checking which one scores higher as {@code targetingBias} moves from "easiest kill" to
 * "biggest threat".
 */
class AttackEvaluatorPersonalityTest {
    // Both targets survive a hit from Attacker's damage (3) — isolates the targeting-bias
    // comparison from the much larger lethal-vs-non-lethal base-score split.
    private static final PieceDefinition ATTACKER = new PieceDefinition("Attacker", 1, 5, 3, 1, 1, List.of());
    private static final PieceDefinition BRUISER = new PieceDefinition("Bruiser", 3, 10, 2, 1, 1, List.of()); // the bigger threat: higher cost/damage
    private static final PieceDefinition WEAKLING = new PieceDefinition("Weakling", 1, 4, 1, 1, 1, List.of()); // the easier kill: low remaining health

    private BoardGrid grid;

    @BeforeEach
    void setUp() {
        GameContext.createHeadless();
        PieceRegistry.register(ATTACKER);
        PieceRegistry.register(BRUISER);
        PieceRegistry.register(WEAKLING);
        CardCatalog.clear();
    }

    @AfterEach
    void tearDown() {
        CardCatalog.clear();
    }

    /**
     * Attacker at (0,2); Bruiser (big threat) at (0,1); Weakling (easy kill) at (0,3) — all on
     * row 0, clear of the standard board's mountains at (3,1)/(3,3).
     */
    private void placeAttackerBetweenABigThreatAndAnEasyKill() {
        MatchConfig config = MatchConfig.standard(GameMode.DEMO, List.of("Attacker"), List.of("Attacker"), 1L, Map.of());
        grid = MatchSetup.prepare(config);

        Entity attacker = PieceFactory.createPiece(ATTACKER, PieceAlignment.P1, 0, 2);
        grid.addEntity(0, 2, attacker);
        // A freshly-created piece starts with "summoning sickness" (0 remaining actions) until
        // a real turn-start reset runs — bypass that here since this test cares only about
        // AttackEvaluator's scoring, not turn flow.
        attacker.getComponent(StatsComponent.class).resetActions();
        Entity bruiser = PieceFactory.createPiece(BRUISER, PieceAlignment.P2, 0, 1);
        grid.addEntity(0, 1, bruiser);
        Entity weakling = PieceFactory.createPiece(WEAKLING, PieceAlignment.P2, 0, 3);
        grid.addEntity(0, 3, weakling);

        // ModifierResolutionSystem only computes ComputedStatsComponent (what EntityUtils.
        // getDamage/getCost read) on an engine tick — these entities were never ticked.
        MatchSetup.tick(0f);
    }

    private List<Intent> attackIntents(BotConfig config) {
        Entity attacker = grid.getEntityAt(0, 2);
        TacticalState tactical = new TacticalState(PieceAlignment.P1,
                List.of(new PieceEntry(new Coord(0, 2), attacker)),
                List.of(new PieceEntry(new Coord(0, 1), grid.getEntityAt(0, 1)),
                        new PieceEntry(new Coord(0, 3), grid.getEntityAt(0, 3))),
                new ThreatMap(grid.getRows(), grid.getCols()));
        List<Intent> intents = new ArrayList<>();
        new AttackEvaluator(config).build(grid, tactical, intents);
        return intents;
    }

    private int scoreFor(List<Intent> intents, int col) {
        for (Intent intent : intents) {
            if (intent.command() instanceof AttackCommand attack && attack.targetCol() == col) return intent.score();
        }
        throw new AssertionError("no attack intent targets column " + col);
    }

    @Test
    void noBias_favorsTheBiggerThreat_unchangedFromBeforeBot005() {
        placeAttackerBetweenABigThreatAndAnEasyKill();
        List<Intent> intents = attackIntents(BotConfig.defaultConfig());
        assertTrue(scoreFor(intents, 1) > scoreFor(intents, 3), "even at bias 0, higher cost/damage already scores higher");
    }

    @Test
    void positiveTargeting_widensTheGapTowardTheBiggerThreat() {
        placeAttackerBetweenABigThreatAndAnEasyKill();
        int baseGap = scoreFor(attackIntents(BotConfig.defaultConfig()), 1) - scoreFor(attackIntents(BotConfig.defaultConfig()), 3);

        BotConfig threatFocused = BotConfig.forPersonality(new BotPersonality(0, 0, 0, 0, 0, 1, 0));
        List<Intent> intents = attackIntents(threatFocused);
        int biasedGap = scoreFor(intents, 1) - scoreFor(intents, 3);

        assertTrue(biasedGap > baseGap, "a threat-focused bot should favor the bruiser even more strongly");
    }

    @Test
    void negativeTargeting_flipsThePreferenceToTheEasyKill() {
        placeAttackerBetweenABigThreatAndAnEasyKill();
        BotConfig opportunist = BotConfig.forPersonality(new BotPersonality(0, 0, 0, 0, 0, -1, 0));
        List<Intent> intents = attackIntents(opportunist);
        assertTrue(scoreFor(intents, 3) > scoreFor(intents, 1), "an opportunistic bot should prefer finishing off the weak target");
    }
}
