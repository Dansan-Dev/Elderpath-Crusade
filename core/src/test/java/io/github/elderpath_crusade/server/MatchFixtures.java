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
import io.github.elderpath_crusade.match.MatchConfig;
import io.github.elderpath_crusade.net.protocol.MatchView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Shared setup for match-server tests: a headless context, pieces/abilities/cards defined in
 * code (no assets), and a replica-vs-server consistency assertion.
 */
final class MatchFixtures {
    static final float TICK = 1f / 60f;

    /** Plain unit: 3 health, 1 damage, speed 2. */
    static final PieceDefinition RUNNER = new PieceDefinition("Runner", 1, 3, 1, 2, 1, List.of());
    /** Runner with "Zap": choose an enemy within 2, deal 2. Costs 1 action + 1 mana. */
    static final PieceDefinition ZAPPER = new PieceDefinition("Zapper", 1, 3, 1, 2, 1, List.of(new AbilityRef("Zap")));
    /** Runner with "Conjure": summon a Runner onto one of its owner's home-row tiles (a TILE pick). */
    static final PieceDefinition CONJURER = new PieceDefinition("Conjurer", 1, 3, 1, 2, 1, List.of(new AbilityRef("Conjure")));

    static final AbilityDefinition ZAP = new AbilityDefinition("Zap", "Zap", Map.of(), List.of(),
            List.of(new ActionDef(List.of(new Cost("Action", 1), new Cost("Mana", 1)),
                    new TargetSelector("ChooseEnemy", Map.of("range", 2)),
                    List.of(new EffectNode("Damage", Map.of("target", "$chosen", "amount", 2))))),
            List.of());
    static final AbilityDefinition CONJURE = new AbilityDefinition("Conjure", "Conjure", Map.of(), List.of(),
            List.of(new ActionDef(List.of(new Cost("Action", 1)), new TargetSelector("Self"),
                    List.of(new EffectNode("SummonPiece", Map.of("piece", "Runner"))))),
            List.of());

    private MatchFixtures() {}

    /** Fresh headless context with the test pieces registered. */
    static void setUpContext() {
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

    static void tearDownContext() {
        CardCatalog.clear();
        TypedEventBus.get().clear();
    }

    static List<String> deckOf(String card, int count) {
        return new ArrayList<>(Collections.nCopies(count, card));
    }

    static MatchConfig config(List<String> p1Deck, List<String> p2Deck, Map<PieceAlignment, BotKind> bots) {
        return MatchConfig.standard(GameMode.ONLINE_MATCH, p1Deck, p2Deck, 7L, bots);
    }

    /** The client's replica equals what the server says that client may see. */
    static void assertConsistent(MatchServer server, MatchClient client) {
        PieceAlignment seat = client.getState().getSeat();
        List<String> diff = MatchView.diff(server.viewFor(seat), client.getState().toView());
        assertEquals(List.of(), diff, seat + "'s replica disagrees with the server");
    }

    static String firstCard(MatchClient client) {
        return client.getState().getOwnHand().get(0).instanceId();
    }
}
