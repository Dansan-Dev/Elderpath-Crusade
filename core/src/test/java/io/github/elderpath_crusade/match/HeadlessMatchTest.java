package io.github.elderpath_crusade.match;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.abilities.data.AbilityDefinition;
import io.github.elderpath_crusade.abilities.data.EffectNode;
import io.github.elderpath_crusade.abilities.data.Reaction;
import io.github.elderpath_crusade.abilities.data.TriggerType;
import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.data.AbilityRef;
import io.github.elderpath_crusade.data.AbilityRegistry;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.ecs.components.SpriteComponent;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.GameWonEvent;
import io.github.elderpath_crusade.events.PieceDiedEvent;
import io.github.elderpath_crusade.events.TurnStartedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game.PlayerManager;
import io.github.elderpath_crusade.server.GameServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plays whole matches with no window: GameContext.createHeadless() + MatchSetup, driven
 * through GameServer/CardRules exactly as clicks (or network commands) would be. Pieces and
 * abilities are defined in code so this runs without assets (cloud profile).
 */
class HeadlessMatchTest {
    private static final float TICK = 1f / 60f;

    /** Fast mover: reaches the goal row in a few turns. */
    private static final PieceDefinition RUNNER = new PieceDefinition("Runner", 1, 3, 1, 2, 1, List.of());
    /** Zaps every adjacent enemy for 1 when summoned. */
    private static final PieceDefinition ZAPPER = new PieceDefinition("Zapper", 1, 1, 1, 1, 1,
            List.of(new AbilityRef("ZapOnSummon", Map.of())));
    /** BasicBot only ever plays Wolf cards. */
    private static final PieceDefinition WOLF = new PieceDefinition("Wolf", 1, 3, 1, 2, 1, List.of());
    private static final AbilityDefinition ZAP_ON_SUMMON = new AbilityDefinition("ZapOnSummon", "", null,
            List.of(new Reaction(TriggerType.ON_SUMMON, null,
                    List.of(new EffectNode("Damage", Map.of("target", "AdjacentEnemies", "amount", 1))))),
            null, null);

    private final List<PieceAlignment> turnsStarted = new ArrayList<>();
    private final List<GameWonEvent> wins = new ArrayList<>();
    private final List<PieceDiedEvent> deaths = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.createHeadless();
        PieceRegistry.register(RUNNER);
        PieceRegistry.register(ZAPPER);
        PieceRegistry.register(WOLF);
        AbilityRegistry.register(ZAP_ON_SUMMON);
        CardCatalog.clear();
        CardCatalog.registerSummon("Runner", "Runner");
        CardCatalog.registerSummon("Zapper", "Zapper");
        CardCatalog.registerSummon("Wolf", "Wolf");
    }

    @AfterEach
    void tearDown() {
        CardCatalog.clear();
        TypedEventBus.get().clear();
    }

    private BoardGrid start(MatchConfig config) {
        BoardGrid grid = MatchSetup.prepare(config);
        // Not session-scoped, so they survive MatchSetup's reset of the session group.
        TypedEventBus.get().register(TurnStartedEvent.class, e -> turnsStarted.add(e.player()));
        TypedEventBus.get().register(GameWonEvent.class, wins::add);
        TypedEventBus.get().register(PieceDiedEvent.class, deaths::add);
        MatchSetup.begin();
        return grid;
    }

    private static List<String> deckOf(String card, int count) {
        return new ArrayList<>(Collections.nCopies(count, card));
    }

    private static PlayerManager.PlayerState player(PieceAlignment alignment) {
        return GameContext.get().getPlayerManager().get(alignment);
    }

    private static String firstCardInHand(PieceAlignment alignment) {
        return player(alignment).hand.getCards().get(0).instanceId();
    }

    private static void endTurn() {
        GameContext.get().getTurnManager().endTurn();
        MatchSetup.tick(TICK);
    }

    @Test
    void fullMatch_summonMoveAbilityAttackTurnsAndWin_runsHeadlessly() {
        assertNull(Gdx.gl, "no GL context anywhere in this test");
        BoardGrid grid = start(MatchConfig.standard(GameMode.LOCAL_MATCH,
                deckOf("Runner", 12), deckOf("Zapper", 12), 42L, Map.of()));
        GameServer server = GameContext.get().getGameServer();

        // Turn 1 (P1): opening hand + mana
        assertEquals(PieceAlignment.P1, GameContext.get().getTurnManager().getCurrentPlayer());
        assertEquals(3, player(PieceAlignment.P1).hand.getCards().size());
        assertEquals(1, player(PieceAlignment.P1).getMana());

        assertTrue(server.playSummonCard(PieceAlignment.P1, firstCardInHand(PieceAlignment.P1), 0, 4));
        Entity runner = grid.getEntityAt(0, 4);
        assertNotNull(runner);
        assertNull(runner.getComponent(SpriteComponent.class), "headless pieces carry no sprite");
        assertEquals(0, player(PieceAlignment.P1).getMana());
        assertEquals(2, player(PieceAlignment.P1).hand.getCards().size());
        assertFalse(server.movePlot(0, 4, 1, 4), "summoning sickness: no actions on the turn it's summoned");

        // Hotseat waits for the next player to take over the screen — the server-side flow
        // for the guest/hosted modes calls the same startNextPlayerTurn.
        endTurnAndHandOver();

        // Turn 2 (P2): fresh hand, mana, then pass
        assertEquals(PieceAlignment.P2, GameContext.get().getTurnManager().getCurrentPlayer());
        assertEquals(3, player(PieceAlignment.P2).hand.getCards().size());
        assertEquals(0, player(PieceAlignment.P1).hand.getCards().size(), "P1's hand is discarded at end of turn");
        endTurnAndHandOver();

        // Turns 3-7 (P1 walks the right lane; P2 passes)
        assertEquals(1, EntityUtils.getRemainingActions(runner), "actions reset at the owner's turn start");
        assertEquals(1, player(PieceAlignment.P1).getMana(), "spent 1 of turn 1's mana, +1 this turn");
        assertTrue(server.movePlot(0, 4, 2, 4));
        assertEquals(0, EntityUtils.getRemainingActions(runner));
        endTurnAndHandOver();
        endTurnAndHandOver();
        assertTrue(server.movePlot(2, 4, 4, 4));
        endTurnAndHandOver();
        endTurnAndHandOver();
        assertTrue(server.movePlot(4, 4, 5, 4));
        endTurnAndHandOver();

        // Turn 8 (P2): summon a Zapper next to the runner — its ON_SUMMON reaction fires
        assertEquals(3, EntityUtils.getCurrentHealth(runner));
        assertTrue(server.playSummonCard(PieceAlignment.P2, firstCardInHand(PieceAlignment.P2), 6, 4));
        MatchSetup.tick(TICK); // AbilityResolverSystem resolves queued reactions on update
        assertEquals(2, EntityUtils.getCurrentHealth(runner), "ZapOnSummon hit the adjacent enemy");
        Entity zapper = grid.getEntityAt(6, 4);
        assertNotNull(zapper);
        endTurnAndHandOver();

        // Turn 9 (P1): attack and kill the Zapper
        assertTrue(server.movePlot(5, 4, 6, 4), "attack the adjacent enemy");
        MatchSetup.tick(TICK); // DeathSystem removes dead pieces on update
        assertNull(grid.getEntityAt(6, 4));
        assertEquals(1, deaths.size());
        assertSame(runner, grid.getEntityAt(5, 4), "the attacker stays put");
        endTurnAndHandOver();
        endTurnAndHandOver();

        // Turn 11 (P1): step onto P2's home row — P1 wins
        assertTrue(wins.isEmpty());
        assertTrue(server.movePlot(5, 4, 6, 4));
        assertEquals(1, wins.size());
        assertEquals(PieceAlignment.P1, wins.get(0).winner());
        assertEquals(PieceAlignment.P1, GameContext.get().getWinConditionManager().getWinner());
        assertNull(Gdx.gl);
    }

    @Test
    void botVsBot_playsTurnsWithoutStalling() {
        for (BotKind kind : BotKind.values()) {
            for (long seed = 1; seed <= 3; seed++) {
                setUp();
                turnsStarted.clear();
                wins.clear();
                String card = kind == BotKind.BASIC ? "Wolf" : "Runner";
                start(MatchConfig.standard(GameMode.DEMO,
                        deckOf(card, 12), deckOf(card, 12), seed,
                        Map.of(PieceAlignment.P1, kind, PieceAlignment.P2, kind)));

                int maxTurns = 60;
                for (int i = 0; i < 200_000 && wins.isEmpty() && turnsStarted.size() < maxTurns; i++) {
                    MatchSetup.tick(0.05f);
                }

                String label = kind + " seed " + seed;
                assertTrue(!wins.isEmpty() || turnsStarted.size() >= maxTurns,
                        label + ": the match stalled after " + turnsStarted.size() + " turns");
                assertTrue(turnsStarted.size() >= 2, label + ": both sides took a turn");
                for (int t = 1; t < turnsStarted.size(); t++) {
                    assertNotEquals(turnsStarted.get(t - 1), turnsStarted.get(t), label + ": turns alternate");
                }
                System.out.println("[botVsBot] " + label + ": " + turnsStarted.size() + " turns, winner="
                        + (wins.isEmpty() ? "none" : wins.get(0).winner()));
            }
        }
    }

    /** Ends the current turn; in hotseat (LOCAL_MATCH) also hands the screen to the next player. */
    private static void endTurnAndHandOver() {
        endTurn();
        if (GameContext.get().getTurnManager().isWaitingForNextPlayer()) {
            GameContext.get().getTurnManager().startNextPlayerTurn();
        }
        MatchSetup.tick(TICK);
    }
}
