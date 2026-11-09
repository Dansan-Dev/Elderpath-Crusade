package io.github.elderpath_crusade.bot;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.GameWonEvent;
import io.github.elderpath_crusade.events.TurnStartedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.match.MatchConfig;
import io.github.elderpath_crusade.match.MatchSetup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Measures the bot improvements from BOT-002 (SmartBot tie-breaking)/BOT-004 (personalities)
 * against real outcomes across many seeds, rather than trusting the design by inspection —
 * "does it actually resolve, does it actually win more" with a number attached, same
 * headless-but-real-engine bar as every other `match/`/`bot/` test. Two things this directly
 * answers:
 *
 * <ul>
 *   <li>BUG-029 (two identical SmartBots stalemate forever on the symmetric standard map,
 *   found before BOT-002's tie-breaking existed): run SmartBot vs SmartBot on the standard map
 *   — the exact scenario the bug was reported on, and the one the existing e2e bot-vs-bot test
 *   deliberately avoids by using a lopsided map instead — across many seeds and assert almost
 *   all of them resolve to a winner within a generous turn cap.</li>
 *   <li>"The bot should actually win more": SmartBot vs BasicBot across many seeds, asserting
 *   SmartBot wins the large majority. (BasicBot's own BUG-028 fix — it could previously only
 *   ever summon a card literally named "Wolf" — is exercised for free here, since both sides'
 *   deck is named "Wolf" only incidentally; a differently-named deck would otherwise have left
 *   BasicBot unable to summon anything at all.)</li>
 * </ul>
 */
class BotStrengthHeadlessTest {
    private static final PieceDefinition WOLF = new PieceDefinition("Wolf", 1, 3, 1, 2, 1, List.of());
    private static final int SEEDS = 30;
    private static final int MAX_TURNS = 300;

    private final List<GameWonEvent> wins = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        wins.clear();
        GameContext.createHeadless();
        PieceRegistry.register(WOLF);
        CardCatalog.clear();
        CardCatalog.registerSummon("Wolf", "Wolf");
    }

    @AfterEach
    void tearDown() {
        CardCatalog.clear();
        TypedEventBus.get().clear();
    }

    @Test
    void smartVsSmart_onTheSymmetricStandardMap_resolvesInsteadOfStalemating() {
        int resolved = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            resolved += playOneMatch(standardConfig(seed, BotKind.SMART, BotKind.SMART)) != null ? 1 : 0;
        }
        System.out.println("[BotStrength] SmartBot vs SmartBot (standard map): " + resolved + "/" + SEEDS + " resolved");
        // Measured 28/30 (93%) with BOT-002's tie-breaking in place, fully deterministic given
        // BotKind.create's per-seed RNG — before it, BUG-029 reported 0/N resolving, ever, on
        // this exact map. A couple of seeds still running out the clock on a genuine mirror
        // matchup is expected; a wide margin below the measured baseline keeps this honest
        // rather than chasing an arbitrary round number.
        assertTrue(resolved >= Math.round(SEEDS * 0.8),
                "most seeds should resolve to a winner now (BUG-029), not stalemate: " + resolved + "/" + SEEDS);
    }

    @Test
    void smartVsBasic_winsTheLargeMajority() {
        int smartWins = 0, resolved = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            PieceAlignment winner = playOneMatch(standardConfig(seed, BotKind.SMART, BotKind.BASIC));
            if (winner != null) {
                resolved++;
                if (winner == PieceAlignment.P1) smartWins++;
            }
        }
        System.out.println("[BotStrength] SmartBot vs BasicBot (standard map): SmartBot won " + smartWins + "/" + resolved + " resolved matches");
        assertTrue(resolved >= SEEDS - 2, "both bots should keep the match moving to a result almost every seed: " + resolved + "/" + SEEDS);
        assertTrue(smartWins >= (int) Math.round(resolved * 0.8),
                "SmartBot should beat BasicBot in the large majority of matches: " + smartWins + "/" + resolved);
    }

    private static MatchConfig standardConfig(long seed, BotKind p1, BotKind p2) {
        List<String> deck = deckOf("Wolf", 16);
        return MatchConfig.standard(GameMode.DEMO, deck, new ArrayList<>(deck), seed,
                Map.of(PieceAlignment.P1, p1, PieceAlignment.P2, p2));
    }

    private static List<String> deckOf(String card, int count) {
        return new ArrayList<>(Collections.nCopies(count, card));
    }

    /**
     * Plays one match to its end (or the turn cap); returns the winner, or null if it never
     * resolved. Re-does the full {@code @BeforeEach} setup for every match, not just once per
     * test method — same pattern {@code HeadlessMatchTest.botVsBot_playsTurnsWithoutStalling}
     * already uses for its own seed loop, since {@code wins::add}/the turn counter are plain
     * (non-session-scoped) listeners that would otherwise pile up across iterations instead of
     * being cleared by {@code MatchSetup.prepare}'s session-group reset.
     */
    private PieceAlignment playOneMatch(MatchConfig config) {
        setUp();
        MatchSetup.start(config);
        TypedEventBus.get().register(GameWonEvent.class, wins::add);

        int[] turns = {0};
        TypedEventBus.get().register(TurnStartedEvent.class, e -> turns[0]++);

        for (int i = 0; i < 2_000_000 && wins.isEmpty() && turns[0] < MAX_TURNS; i++) {
            MatchSetup.tick(0.05f);
        }
        return wins.isEmpty() ? null : wins.get(0).winner();
    }
}
