package io.github.elderpath_crusade.match;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game.PlayerManager;
import io.github.elderpath_crusade.match.ActionResult.Reason;
import io.github.elderpath_crusade.run.RunCardModification;
import io.github.elderpath_crusade.server.GameServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ELDER-006's "Elders are real pieces" mechanic through the real MatchSetup/GameServer pipeline:
 * every {@code MatchConfig.elders()} name spawns as an actual, visible piece on that side's home
 * row before the match begins — the Elder's own stats/abilities, present from turn 1, not played
 * from hand. Headless, same bar as CardLockTest/MatchSetupCardModificationTest (the other half
 * of the Elder mechanic — the card-lock timer — already has its own test there).
 */
class ElderPieceSpawnTest {
    private static final PieceDefinition WOLF = new PieceDefinition("Wolf", 1, 3, 2, 1, 2, List.of());

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

    private BoardGrid prepare(MatchConfig config) {
        grid = MatchSetup.prepare(config);
        return grid;
    }

    private static MatchConfig baseConfig() {
        return MatchConfig.standard(GameMode.ROUGELIKE_RUN,
                List.of("Wolf", "Wolf", "Wolf", "Wolf"), List.of("Wolf", "Wolf"), 3L, Map.of());
    }

    @Test
    void noElders_homeRowStaysEmptyAtMatchStart() {
        prepare(baseConfig());
        for (int col = 0; col < grid.getCols(); col++) {
            assertFalse(grid.isOccupied(0, col), "no P1 elders configured — nothing should pre-occupy its home row");
        }
    }

    @Test
    void singleElder_spawnsOnTheHomeRowWithTheCardsBaseStats() {
        prepare(baseConfig().withElders(Map.of(PieceAlignment.P1, List.of("Wolf"))));
        MatchSetup.begin();

        Entity elder = grid.getEntityAt(0, 2); // 1 elder on a 5-col board centers at col 2
        assertNotNull(elder, "the Elder should be on P1's home row (row 0) from the start");
        assertEquals(PieceAlignment.P1, EntityUtils.getAlignment(elder));
        assertEquals(WOLF.damage(), EntityUtils.getDamage(elder));
        assertEquals(WOLF.health(), EntityUtils.getMaxHealth(elder));
        assertEquals(WOLF.health(), EntityUtils.getCurrentHealth(elder));
    }

    @Test
    void elderPiece_hasFullActionsRightAfterBeginWithNoEngineTickYet() {
        // The regression this guards: ComputedStatsComponent defaults to 0 actions, and
        // TurnSystem's turn-1 action reset (inside begin()) reads it before any tick ever
        // computes it for a piece that existed before turn 1 — see spawnElderPiece's javadoc.
        prepare(baseConfig().withElders(Map.of(PieceAlignment.P1, List.of("Wolf"))));
        MatchSetup.begin();

        Entity elder = grid.getEntityAt(0, 2);
        assertFalse(EntityUtils.isExhausted(elder), "an Elder must not look exhausted on the very first turn");
        assertEquals(WOLF.actions(), EntityUtils.getRemainingActions(elder));
    }

    @Test
    void multipleElders_spawnAtDistinctColumnsOnTheSameHomeRow() {
        prepare(baseConfig().withElders(Map.of(PieceAlignment.P1, List.of("Wolf", "Wolf", "Wolf"))));
        MatchSetup.begin();

        int occupied = 0;
        for (int col = 0; col < grid.getCols(); col++) {
            if (grid.isOccupied(0, col)) occupied++;
        }
        assertEquals(3, occupied, "each of the 3 Elders gets its own home-row tile");
    }

    @Test
    void bothSides_spawnTheirOwnEldersOnTheirOwnHomeRow() {
        prepare(baseConfig().withElders(Map.of(
                PieceAlignment.P1, List.of("Wolf"),
                PieceAlignment.P2, List.of("Wolf"))));
        MatchSetup.begin();

        Entity p1Elder = grid.getEntityAt(grid.getHomeRow(PieceAlignment.P1), 2);
        Entity p2Elder = grid.getEntityAt(grid.getHomeRow(PieceAlignment.P2), 2);
        assertNotNull(p1Elder);
        assertNotNull(p2Elder);
        assertEquals(PieceAlignment.P1, EntityUtils.getAlignment(p1Elder));
        assertEquals(PieceAlignment.P2, EntityUtils.getAlignment(p2Elder));
    }

    @Test
    void modifiedElder_resolvesItsSyntheticNameAndAppliesTheDelta() {
        prepare(baseConfig()
                .withElders(Map.of(PieceAlignment.P1, List.of("Wolf#run-9")))
                .withCardMods(Map.of(PieceAlignment.P1,
                        Map.of("run-9", new RunCardModification(Map.of("damage", 2), List.of())))));
        MatchSetup.begin();

        Entity elder = grid.getEntityAt(0, 2);
        assertNotNull(elder, "a modified Elder must still resolve to a real piece via its synthetic name");
        assertEquals(WOLF.damage() + 2, EntityUtils.getDamage(elder), "base 2 + the Elder's own modification");
    }

    @Test
    void elderPiece_occupiesItsHomeRowTileSoANormalSummonThereIsRejected() {
        prepare(baseConfig().withElders(Map.of(PieceAlignment.P1, List.of("Wolf"))));
        MatchSetup.begin();
        PlayerManager.PlayerState p1 = GameContext.get().getPlayerManager().get(PieceAlignment.P1);
        String handCardId = p1.hand.getCards().get(0).instanceId();

        ActionResult result = server.playSummonCard(PieceAlignment.P1, handCardId, 0, 2);

        assertEquals(Reason.INVALID_SUMMON_TARGET, result.reason(),
                "the Elder already occupies that home-row tile — a real board cost for a real piece");
    }
}
