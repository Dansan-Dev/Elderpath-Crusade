package io.github.elderpath_crusade.match;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.abilities.data.AbilityDefinition;
import io.github.elderpath_crusade.abilities.data.ActionDef;
import io.github.elderpath_crusade.abilities.data.Cost;
import io.github.elderpath_crusade.abilities.data.EffectNode;
import io.github.elderpath_crusade.abilities.data.ModifierDef;
import io.github.elderpath_crusade.abilities.data.Reaction;
import io.github.elderpath_crusade.abilities.data.TargetSelector;
import io.github.elderpath_crusade.abilities.data.TriggerType;
import io.github.elderpath_crusade.data.AbilityRegistry;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.data.RelicRegistry;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.ecs.components.ComputedStatsComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.ecs.factory.PieceFactory;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.run.RelicDefinition;
import io.github.elderpath_crusade.server.GameServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UPG-001's carrier-piece mechanism end to end, for each relic "shape" an AbilityDefinition
 * already supports — passive aura, triggered reaction, player-activated action — proving a
 * relic's in-battle effect needs zero new engine code, just a carrier piece (RelicCarrierIds)
 * MatchSetup.prepare spawns from MatchConfig.relics(side) + RelicRegistry + AbilityRegistry.
 * Headless, through the real GameServer/MatchSetup pipeline — the same bar as
 * AbilityActivationTest, HeadlessMatchTest.
 */
class RelicCarrierTest {
    private static final PieceDefinition RUNNER = new PieceDefinition("Runner", 1, 3, 1, 2, 1, List.of());

    private GameServer server;
    private BoardGrid grid;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.createHeadless();
        PieceRegistry.register(RUNNER);
        RelicRegistry.clear();
        server = GameContext.get().getGameServer();
    }

    @AfterEach
    void tearDown() {
        TypedEventBus.get().clear();
        RelicRegistry.clear();
    }

    private BoardGrid prepare(Map<PieceAlignment, List<String>> relics) {
        List<String> deck = new ArrayList<>(Collections.nCopies(12, "Runner"));
        MatchConfig config = MatchConfig.standard(GameMode.ROUGELIKE_RUN, deck, new ArrayList<>(deck), 3L, Map.of())
                .withRelics(relics);
        return MatchSetup.prepare(config);
    }

    private Entity placePiece(PieceAlignment owner, int row, int col) {
        Entity e = PieceFactory.createFromDefinition(GameContext.get().getEcsEngine(), RUNNER, owner,
                UUID.randomUUID().toString(), row, col);
        e.add(new ComputedStatsComponent()); // createFromDefinition skips it; ModifierResolutionSystem needs it to compute effective stats
        grid.addEntity(row, col, e);
        return e;
    }

    @Test
    void passiveRelic_buffsAllFriendlyUnits() {
        AbilityRegistry.register(new AbilityDefinition("PackLeaderAura", "desc", null, List.of(), List.of(),
                List.of(new ModifierDef(new TargetSelector("AllFriendlyUnits"), Map.of("addDamage", 1)))));
        RelicRegistry.register(RelicDefinition.battleOnly("PackLeader", "desc", List.of("PackLeaderAura")));

        grid = prepare(Map.of(PieceAlignment.P1, List.of("PackLeader")));
        Entity friendly = placePiece(PieceAlignment.P1, 5, 4); // far from the carrier — AllFriendlyUnits ignores position
        MatchSetup.begin();
        MatchSetup.tick(0f);

        assertEquals(2, EntityUtils.getDamage(friendly), "base 1 + PackLeader's +1");
    }

    @Test
    void passiveRelic_doesNotAffectTheOtherSide() {
        AbilityRegistry.register(new AbilityDefinition("PackLeaderAura", "desc", null, List.of(), List.of(),
                List.of(new ModifierDef(new TargetSelector("AllFriendlyUnits"), Map.of("addDamage", 1)))));
        RelicRegistry.register(RelicDefinition.battleOnly("PackLeader", "desc", List.of("PackLeaderAura")));

        grid = prepare(Map.of(PieceAlignment.P1, List.of("PackLeader")));
        Entity enemy = placePiece(PieceAlignment.P2, 5, 4);
        MatchSetup.begin();
        MatchSetup.tick(0f);

        assertEquals(1, EntityUtils.getDamage(enemy), "P2 has no relic — unaffected by P1's PackLeader");
    }

    @Test
    void triggeredRelic_healsAllFriendlyUnitsOnTurnStart() {
        AbilityRegistry.register(new AbilityDefinition("BonfireWarmth", "desc", null,
                List.of(new Reaction(TriggerType.ON_TURN_START, List.of(),
                        List.of(new EffectNode("Heal", Map.of("target", "AllFriendlyUnits", "amount", 1))))),
                List.of(), List.of()));
        RelicRegistry.register(RelicDefinition.battleOnly("Bonfire", "desc", List.of("BonfireWarmth")));

        grid = prepare(Map.of(PieceAlignment.P1, List.of("Bonfire")));
        Entity friendly = placePiece(PieceAlignment.P1, 5, 4);
        friendly.getComponent(StatsComponent.class).currentHealth = 2; // damaged, below its max of 3

        MatchSetup.begin(); // P1's first turn starting emits TurnStartedEvent(P1)
        MatchSetup.tick(0f); // AbilityResolverSystem is queue-based — drains on the next engine update

        assertEquals(3, EntityUtils.getCurrentHealth(friendly), "healed back to max by Bonfire's ON_TURN_START reaction");
    }

    @Test
    void triggeredRelic_doesNotFireForTheOtherSidesTurn() {
        AbilityRegistry.register(new AbilityDefinition("BonfireWarmth", "desc", null,
                List.of(new Reaction(TriggerType.ON_TURN_START, List.of(),
                        List.of(new EffectNode("Heal", Map.of("target", "AllFriendlyUnits", "amount", 1))))),
                List.of(), List.of()));
        RelicRegistry.register(RelicDefinition.battleOnly("Bonfire", "desc", List.of("BonfireWarmth")));

        // P2 owns the relic, but P1 goes first — P2's carrier shouldn't fire on P1's turn start.
        grid = prepare(Map.of(PieceAlignment.P2, List.of("Bonfire")));
        Entity p2Piece = placePiece(PieceAlignment.P2, 5, 4);
        p2Piece.getComponent(StatsComponent.class).currentHealth = 2;

        MatchSetup.begin();
        MatchSetup.tick(0f);

        assertEquals(2, EntityUtils.getCurrentHealth(p2Piece), "P1's turn starting must not trigger P2's relic");
    }

    @Test
    void activeRelic_damagesAllEnemiesWhenActivated() {
        AbilityDefinition hornBlast = new AbilityDefinition("HornBlast", "desc", Map.of(), List.of(),
                List.of(new ActionDef(List.of(new Cost("Action", 1)), new TargetSelector("Self"),
                        List.of(new EffectNode("ForEach", Map.of(
                                "targets", Map.of("type", "AllEnemyUnits"),
                                "do", List.of(Map.of("type", "Damage", "params", Map.of("target", "$target", "amount", 1)))))))),
                List.of());
        AbilityRegistry.register(hornBlast);
        RelicRegistry.register(RelicDefinition.battleOnly("Horn", "desc", List.of("HornBlast")));

        grid = prepare(Map.of(PieceAlignment.P1, List.of("Horn")));
        Entity enemy = placePiece(PieceAlignment.P2, 5, 4);
        MatchSetup.begin();
        MatchSetup.tick(0f); // turn-start's action reset (TurnSystem) is also queue-driven

        ActionResult result = server.activateAbility(PieceAlignment.P1, RelicCarrierIds.idFor(PieceAlignment.P1), 0);

        assertTrue(result.isOk(), "expected ok, got " + result);
        assertEquals(2, EntityUtils.getCurrentHealth(enemy), "3 base - 1 from Horn's active");
    }

    @Test
    void carrier_isNotSpawnedForASideWithNoRelics() {
        grid = prepare(Map.of());
        MatchSetup.begin();

        ActionResult result = server.activateAbility(PieceAlignment.P1, RelicCarrierIds.idFor(PieceAlignment.P1), 0);

        assertEquals(ActionResult.Reason.NO_PIECE, result.reason());
    }
}
