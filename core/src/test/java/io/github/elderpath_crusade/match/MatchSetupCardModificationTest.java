package io.github.elderpath_crusade.match;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.abilities.data.AbilityDefinition;
import io.github.elderpath_crusade.abilities.data.ModifierDef;
import io.github.elderpath_crusade.abilities.data.TargetSelector;
import io.github.elderpath_crusade.data.AbilityRegistry;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.run.RunCardModification;
import io.github.elderpath_crusade.server.GameServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UPG-003's synthetic registry-key mechanism through the real MatchSetup/GameServer pipeline —
 * a run-modified deck instance (SyntheticCardNames-encoded name + its RunCardModification,
 * carried by MatchConfig.cardMods — LAUNCH-001) plays exactly like any other card, with its
 * stat delta and/or added ability baked into a one-off PieceDefinition/CardCatalog.Summon entry
 * MatchSetup.prepare registers before the match begins — zero new engine code. Headless, same
 * bar as RelicCarrierTest/AbilityActivationTest.
 */
class MatchSetupCardModificationTest {
    private static final PieceDefinition WOLF = new PieceDefinition("Wolf", 2, 3, 1, 1, 1, List.of());

    private GameServer server;
    private BoardGrid grid;

    @BeforeEach
    void setUp() {
        GameContext.createHeadless();
        PieceRegistry.register(WOLF);
        CardCatalog.clear();
        CardCatalog.registerSummon("Wolf", "Wolf");
        server = GameContext.get().getGameServer();
    }

    @AfterEach
    void tearDown() {
        CardCatalog.clear();
    }

    private BoardGrid prepare(List<String> p1Deck, Map<String, RunCardModification> p1CardMods) {
        MatchConfig config = MatchConfig.standard(GameMode.ROUGELIKE_RUN, p1Deck, List.of("Wolf"), 3L, Map.of())
                .withCardMods(Map.of(PieceAlignment.P1, p1CardMods));
        BoardGrid grid = MatchSetup.prepare(config);
        GameContext.get().getPlayerManager().get(PieceAlignment.P1).setMana(10);
        return grid;
    }

    @Test
    void statDeltaModification_appliesToTheSpawnedPiecesBaseStats() {
        grid = prepare(List.of("Wolf#run-1"),
                Map.of("run-1", new RunCardModification(Map.of("damage", 2, "health", -1), List.of())));
        MatchSetup.begin();

        ActionResult result = server.playSummonCard(PieceAlignment.P1, "P1-0", 0, 2);
        MatchSetup.tick(0f); // ModifierResolutionSystem computes ComputedStatsComponent from the (unmodified) base

        assertTrue(result.isOk(), "expected ok, got " + result);
        Entity piece = grid.getEntityAt(0, 2);
        assertEquals(3, EntityUtils.getDamage(piece), "base 1 + mod's +2");
        assertEquals(2, EntityUtils.getMaxHealth(piece), "base 3 - mod's 1");
    }

    @Test
    void addedAbilityModification_attachesToTheSpawnedPiece() {
        AbilityRegistry.register(new AbilityDefinition("Frenzy", "desc", null, List.of(), List.of(),
                List.of(new ModifierDef(new TargetSelector("Self"), Map.of("addDamage", 5)))));

        grid = prepare(List.of("Wolf#run-1"),
                Map.of("run-1", new RunCardModification(Map.of(), List.of("Frenzy"))));
        MatchSetup.begin();
        server.playSummonCard(PieceAlignment.P1, "P1-0", 0, 2);
        MatchSetup.tick(0f); // PassiveModifierSystem resolves Frenzy's Self aura, then ModifierResolutionSystem computes it

        Entity piece = grid.getEntityAt(0, 2);
        assertEquals(6, EntityUtils.getDamage(piece), "base 1 + Frenzy's Self +5");
    }

    @Test
    void unmodifiedDeck_hasNoSyntheticNamesAndPlaysNormally() {
        grid = prepare(List.of("Wolf"), Map.of());
        MatchSetup.begin();

        ActionResult result = server.playSummonCard(PieceAlignment.P1, "P1-0", 0, 2);
        MatchSetup.tick(0f); // ModifierResolutionSystem computes ComputedStatsComponent from the base

        assertTrue(result.isOk());
        assertEquals(1, EntityUtils.getDamage(grid.getEntityAt(0, 2)));
    }

    @Test
    void reregisteringTheSameInstanceId_overwritesRatherThanStayingStale() {
        prepare(List.of("Wolf#run-1"), Map.of("run-1", new RunCardModification(Map.of("damage", 1), List.of())));
        assertEquals(2, PieceRegistry.get(PieceRegistry.toRegistryKey("Wolf#run-1")).damage());

        grid = prepare(List.of("Wolf#run-1"), Map.of("run-1", new RunCardModification(Map.of("damage", 4), List.of())));
        MatchSetup.begin();
        server.playSummonCard(PieceAlignment.P1, "P1-0", 0, 2);
        MatchSetup.tick(0f);

        assertEquals(5, EntityUtils.getDamage(grid.getEntityAt(0, 2)), "the second prepare()'s modification must win, not the first's");
    }
}
