package io.github.elderpath_crusade.server;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.abilities.data.AbilityDefinition;
import io.github.elderpath_crusade.abilities.data.ActionDef;
import io.github.elderpath_crusade.abilities.data.Cost;
import io.github.elderpath_crusade.abilities.data.EffectNode;
import io.github.elderpath_crusade.abilities.data.TargetSelector;
import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.client.MatchClient;
import io.github.elderpath_crusade.data.AbilityRef;
import io.github.elderpath_crusade.data.AbilityRegistry;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.MatchConfig;
import io.github.elderpath_crusade.net.protocol.MatchView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared setup for match-server tests: a headless context, pieces/abilities/cards defined in
 * code (no assets), and a replica-vs-server consistency assertion.
 */
public final class MatchFixtures {
    public static final float TICK = 1f / 60f;

    /** Plain unit: 3 health, 1 damage, speed 2. */
    public static final PieceDefinition RUNNER = new PieceDefinition("Runner", 1, 3, 1, 2, 1, List.of());
    /** Runner with "Zap": choose an enemy within 2, deal 2. Costs 1 action + 1 mana. */
    public static final PieceDefinition ZAPPER = new PieceDefinition("Zapper", 1, 3, 1, 2, 1, List.of(new AbilityRef("Zap")));
    /** Runner with "Conjure": summon a Runner onto one of its owner's home-row tiles (a TILE pick). */
    public static final PieceDefinition CONJURER = new PieceDefinition("Conjurer", 1, 3, 1, 2, 1, List.of(new AbilityRef("Conjure")));

    public static final AbilityDefinition ZAP = new AbilityDefinition("Zap", "Zap", Map.of(), List.of(),
            List.of(new ActionDef(List.of(new Cost("Action", 1), new Cost("Mana", 1)),
                    new TargetSelector("ChooseEnemy", Map.of("range", 2)),
                    List.of(new EffectNode("Damage", Map.of("target", "$chosen", "amount", 2))))),
            List.of());
    public static final AbilityDefinition CONJURE = new AbilityDefinition("Conjure", "Conjure", Map.of(), List.of(),
            List.of(new ActionDef(List.of(new Cost("Action", 1)), new TargetSelector("Self"),
                    List.of(new EffectNode("SummonPiece", Map.of("piece", "Runner"))))),
            List.of());

    private MatchFixtures() {}

    /** Fresh headless context with the test pieces registered. */
    public static void setUpContext() {
        TypedEventBus.get().clear();
        GameContext.createHeadless();
        PieceRegistry.register(RUNNER);
        PieceRegistry.register(ZAPPER);
        PieceRegistry.register(CONJURER);
        AbilityRegistry.register(ZAP);
        AbilityRegistry.register(CONJURE);
        CardCatalog.clear();
        CardCatalog.registerSummon("Runner", "Runner");
        CardCatalog.registerSummon("Zapper", "Zapper");
        CardCatalog.registerSummon("Conjurer", "Conjurer");
    }

    public static void tearDownContext() {
        CardCatalog.clear();
        TypedEventBus.get().clear();
    }

    public static List<String> deckOf(String card, int count) {
        return new ArrayList<>(Collections.nCopies(count, card));
    }

    public static MatchConfig config(List<String> p1Deck, List<String> p2Deck, Map<PieceAlignment, BotKind> bots) {
        return MatchConfig.standard(GameMode.ONLINE_MATCH, p1Deck, p2Deck, 7L, bots);
    }

    /**
     * Mountains one row off center (the pre-DESIGN-001 layout). Only for tests that need two identical
     * SmartBots to reach a winner: on the symmetric standard map they mirror each other into a
     * stalemate (BUG-029), which says nothing about whether the server can finish a bot match.
     */
    public static final List<Coord> LOPSIDED_TERRAIN = List.of(new Coord(2, 1), new Coord(2, 3));

    public static MatchConfig lopsidedConfig(List<String> p1Deck, List<String> p2Deck, Long seed,
                                             Map<PieceAlignment, BotKind> bots) {
        return new MatchConfig(MatchConfig.DEFAULT_ROWS, MatchConfig.DEFAULT_COLS, LOPSIDED_TERRAIN,
                Map.of(PieceAlignment.P1, p1Deck, PieceAlignment.P2, p2Deck), seed, GameMode.ONLINE_MATCH, bots);
    }

    /** The client's replica equals what the server says that client may see. */
    public static void assertConsistent(MatchServer server, MatchClient client) {
        PieceAlignment seat = client.getState().getSeat();
        List<String> diff = MatchView.diff(server.viewFor(seat), client.getState().toView());
        assertEquals(List.of(), diff, seat + "'s replica disagrees with the server");
        assertEquals(server.legalActionsFor(seat), client.getState().getLegalActions(),
                seat + "'s LegalActions disagree with what the server would accept");
    }

    public static String firstCard(MatchClient client) {
        return client.getState().getOwnHand().get(0).instanceId();
    }

    /** The opponent's card ids and names never reach this client, except when a card is played. */
    public static void assertNoForeignCardLeak(List<String> rawLines, String opponent) {
        for (String line : rawLines) {
            assertFalse(line.contains("\"" + opponent + "-"), "an opponent's real card id leaked: " + line);
            if (line.contains("\"CardDrawnEvent\"") && line.contains("\"owner\":\"" + opponent + "\"")) {
                assertTrue(line.contains("\"cardName\":null"), "an opponent's drawn card was named: " + line);
            }
        }
    }
}
