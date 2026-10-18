package io.github.elderpath_crusade.match;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.abilities.data.AbilityDefinition;
import io.github.elderpath_crusade.abilities.data.ActionDef;
import io.github.elderpath_crusade.abilities.data.Cost;
import io.github.elderpath_crusade.abilities.data.EffectExecutor;
import io.github.elderpath_crusade.abilities.data.EffectNode;
import io.github.elderpath_crusade.abilities.data.ExpressionContext;
import io.github.elderpath_crusade.abilities.data.TargetSelector;
import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.ecs.components.AbilityInstanceComponent;
import io.github.elderpath_crusade.ecs.components.IdentityComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.ecs.components.StunComponent;
import io.github.elderpath_crusade.ecs.factory.PieceFactory;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.match.ActionResult.Reason;
import io.github.elderpath_crusade.server.GameServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ability activation as a validated server action (ARCH-021) and its picks going through the
 * PickRouter (ARCH-020) — for a human (a test responder standing in for the UI) and a bot.
 * Headless, standard 7x5 board, terrain at (2,1) and (2,3), P1 to act.
 */
class AbilityActivationTest {
    private static final PieceDefinition RUNNER = new PieceDefinition("Runner", 1, 3, 1, 2, 1, List.of());

    /** Self: 1 damage to itself. Costs 1 action. */
    private static final AbilityDefinition SELF_HURT = ability("SelfHurt",
            new ActionDef(List.of(new Cost("Action", 1)), new TargetSelector("Self"),
                    List.of(new EffectNode("Damage", Map.of("target", "$self", "amount", 1)))));
    /** ChooseEnemy within 2: 2 damage. Costs 1 action + 1 mana. */
    private static final AbilityDefinition ZAP = ability("Zap",
            new ActionDef(List.of(new Cost("Action", 1), new Cost("Mana", 1)), new TargetSelector("ChooseEnemy", Map.of("range", 2)),
                    List.of(new EffectNode("Damage", Map.of("target", "$chosen", "amount", 2)))));
    /** WarpMage-style Displace: ChooseAny within 2, then an empty tile next to it. */
    private static final AbilityDefinition DISPLACE = ability("Displace",
            new ActionDef(List.of(new Cost("Action", 1)), new TargetSelector("ChooseAny", Map.of("range", 2)),
                    List.of(new EffectNode("Move", Map.of("target", "$chosen", "destination", "$chosenTile")))));
    /** ChooseRow: 1 damage to every unit in the row. */
    private static final AbilityDefinition ROW_BLAST = ability("RowBlast",
            new ActionDef(List.of(new Cost("Action", 1)), new TargetSelector("ChooseRow"),
                    List.of(new EffectNode("ForEach", Map.of(
                            "targets", Map.of("type", "UnitsInRow", "params", Map.of("row", "$chosen")),
                            "do", List.of(Map.of("type", "Damage", "params", Map.of("target", "$target", "amount", 1))))))));

    private static AbilityDefinition ability(String id, ActionDef action) {
        return new AbilityDefinition(id, id, Map.of(), List.of(), List.of(action), List.of());
    }

    /** Stands in for a player's screen: remembers the prompt, answers when told. */
    private static final class Screen implements PickResponder {
        PickRequest prompted;

        @Override
        public void onPickRequested(PickRequest request, PickRouter router) {
            prompted = request;
        }

        @Override
        public void onPickCancelled(PickRequest request) {
            prompted = null;
        }
    }

    private GameServer server;
    private BoardGrid grid;
    private Screen screen;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.createHeadless();
        PieceRegistry.register(RUNNER);
        server = GameContext.get().getGameServer();
        screen = new Screen();
        GameContext.get().getPickRouter().setLocalResponder(screen);
        grid = start(Map.of());
    }

    @AfterEach
    void tearDown() {
        TypedEventBus.get().clear();
    }

    private BoardGrid start(Map<PieceAlignment, BotKind> bots) {
        List<String> deck = new ArrayList<>(Collections.nCopies(12, "Runner"));
        BoardGrid g = MatchSetup.prepare(MatchConfig.standard(GameMode.DEMO, deck, new ArrayList<>(deck), 3L, bots));
        MatchSetup.begin();
        return g;
    }

    private Entity piece(PieceAlignment owner, int row, int col, AbilityDefinition... abilities) {
        Entity e = PieceFactory.createFromDefinition(GameContext.get().getEcsEngine(), RUNNER, owner,
                UUID.randomUUID().toString(), row, col);
        e.getComponent(StatsComponent.class).remainingActions = 1;
        AbilityInstanceComponent aic = new AbilityInstanceComponent();
        for (AbilityDefinition a : abilities) aic.addAbility(a);
        e.add(aic);
        grid.addEntity(row, col, e);
        return e;
    }

    private static String id(Entity e) {
        return e.getComponent(IdentityComponent.class).id;
    }

    private static int health(Entity e) {
        return e.getComponent(StatsComponent.class).currentHealth;
    }

    private static int actions(Entity e) {
        return e.getComponent(StatsComponent.class).remainingActions;
    }

    private void setMana(PieceAlignment side, int mana) {
        GameContext.get().getPlayerManager().get(side).setMana(mana);
    }

    private Reason answer(PieceAlignment actor, Coord choice) {
        return server.answerPick(actor, PickResponse.chose(screen.prompted.pickId(), choice)).reason();
    }

    // --- validation ---

    @Test
    void rejections_eachReason() {
        Entity mine = piece(PieceAlignment.P1, 0, 0, SELF_HURT, ZAP);
        Entity theirs = piece(PieceAlignment.P2, 6, 0, SELF_HURT);

        assertEquals(Reason.NOT_YOUR_TURN, server.activateAbility(PieceAlignment.P2, id(theirs), 0).reason());
        assertEquals(Reason.NOT_YOUR_TURN, server.activateAbility(null, id(mine), 0).reason());
        assertEquals(Reason.NO_PIECE, server.activateAbility(PieceAlignment.P1, "no-such-piece", 0).reason());
        assertEquals(Reason.NOT_YOUR_PIECE, server.activateAbility(PieceAlignment.P1, id(theirs), 0).reason());
        assertEquals(Reason.NO_SUCH_ABILITY, server.activateAbility(PieceAlignment.P1, id(mine), 2).reason());
        assertEquals(Reason.NO_SUCH_ABILITY, server.activateAbility(PieceAlignment.P1, id(mine), -1).reason());

        setMana(PieceAlignment.P1, 0);
        assertEquals(Reason.NOT_ENOUGH_MANA, server.activateAbility(PieceAlignment.P1, id(mine), 1).reason());

        StunComponent stun = new StunComponent();
        stun.turnsRemaining = 1;
        mine.add(stun);
        assertEquals(Reason.PIECE_STUNNED, server.activateAbility(PieceAlignment.P1, id(mine), 0).reason());
        mine.remove(StunComponent.class);

        mine.getComponent(StatsComponent.class).remainingActions = 0;
        assertEquals(Reason.PIECE_EXHAUSTED, server.activateAbility(PieceAlignment.P1, id(mine), 0).reason());
        assertEquals(3, health(mine), "nothing ran");
    }

    @Test
    void noActiveMatch_rejected() {
        Entity mine = piece(PieceAlignment.P1, 0, 0, SELF_HURT);
        GameContext.get().clearBoard();
        assertEquals(Reason.NO_ACTIVE_MATCH, server.activateAbility(PieceAlignment.P1, id(mine), 0).reason());
    }

    @Test
    void targetedAbility_withNoValidTarget_rejected_noPickNoCost() {
        Entity mine = piece(PieceAlignment.P1, 0, 0, ZAP);
        setMana(PieceAlignment.P1, 1);
        assertEquals(Reason.NO_VALID_TARGET, server.activateAbility(PieceAlignment.P1, id(mine), 0).reason());
        assertNull(screen.prompted);
        assertEquals(1, actions(mine));
        assertEquals(1, GameContext.get().getPlayerManager().get(PieceAlignment.P1).getMana());
    }

    // --- happy paths ---

    @Test
    void self_resolvesImmediately_andPays() {
        Entity mine = piece(PieceAlignment.P1, 0, 0, SELF_HURT);
        assertEquals(Reason.OK, server.activateAbility(PieceAlignment.P1, id(mine), 0).reason());
        assertEquals(2, health(mine));
        assertEquals(0, actions(mine));
    }

    @Test
    void chooseEnemy_asksThePlayer_resolvesOnTheAnswer_andPays() {
        Entity mine = piece(PieceAlignment.P1, 0, 0, ZAP);
        Entity near = piece(PieceAlignment.P2, 2, 0);
        Entity far = piece(PieceAlignment.P2, 5, 0);
        setMana(PieceAlignment.P1, 2);

        assertEquals(Reason.OK, server.activateAbility(PieceAlignment.P1, id(mine), 0).reason());
        assertEquals(PickKind.PIECE, screen.prompted.kind());
        assertEquals(List.of(new Coord(2, 0)), screen.prompted.candidates(), "only enemies within range 2");
        assertEquals(1, actions(mine), "not paid before the answer");

        assertEquals(Reason.INVALID_PICK, answer(PieceAlignment.P1, new Coord(5, 0)), "out of range — not a candidate");
        assertEquals(Reason.OK, answer(PieceAlignment.P1, new Coord(2, 0)));
        assertEquals(1, health(near));
        assertEquals(3, health(far));
        assertEquals(0, actions(mine));
        assertEquals(1, GameContext.get().getPlayerManager().get(PieceAlignment.P1).getMana());
    }

    @Test
    void cancelledPick_noEffect_noCost() {
        Entity mine = piece(PieceAlignment.P1, 0, 0, ZAP);
        Entity enemy = piece(PieceAlignment.P2, 1, 0);
        setMana(PieceAlignment.P1, 1);

        server.activateAbility(PieceAlignment.P1, id(mine), 0);
        assertEquals(Reason.OK, server.answerPick(PieceAlignment.P1, PickResponse.cancelled(screen.prompted.pickId())).reason());
        assertEquals(3, health(enemy));
        assertEquals(1, actions(mine));
        assertEquals(1, GameContext.get().getPlayerManager().get(PieceAlignment.P1).getMana());
    }

    @Test
    void pendingPick_blocksTheOwnersOtherActions() {
        Entity mine = piece(PieceAlignment.P1, 0, 0, ZAP);
        piece(PieceAlignment.P2, 1, 0);
        Entity other = piece(PieceAlignment.P1, 0, 4, SELF_HURT);
        setMana(PieceAlignment.P1, 1);

        server.activateAbility(PieceAlignment.P1, id(mine), 0);
        assertEquals(Reason.PICK_PENDING, server.activateAbility(PieceAlignment.P1, id(other), 0).reason());
        assertEquals(Reason.PICK_PENDING, server.movePlot(PieceAlignment.P1, 0, 4, 1, 4).reason());
        assertEquals(Reason.PICK_PENDING, server.endTurn(PieceAlignment.P1).reason());

        answer(PieceAlignment.P1, new Coord(1, 0));
        assertEquals(Reason.OK, server.movePlot(PieceAlignment.P1, 0, 4, 1, 4).reason(), "unblocked once answered");
    }

    @Test
    void displace_piecePickThenTilePick_movesTarget() {
        Entity mage = piece(PieceAlignment.P1, 2, 0, DISPLACE);
        Entity enemy = piece(PieceAlignment.P2, 2, 1);

        assertEquals(Reason.OK, server.activateAbility(PieceAlignment.P1, id(mage), 0).reason());
        assertEquals(Reason.OK, answer(PieceAlignment.P1, new Coord(2, 1)));
        assertEquals(PickKind.TILE, screen.prompted.kind(), "follow-up: where to put it");
        assertFalse(screen.prompted.candidates().contains(new Coord(2, 0)), "occupied tiles aren't offered");
        assertFalse(screen.prompted.candidates().contains(new Coord(3, 1)), "terrain isn't offered");
        assertEquals(1, actions(mage), "paid only after the whole action resolves");

        assertEquals(Reason.OK, answer(PieceAlignment.P1, new Coord(2, 2)));
        assertSame(enemy, grid.getEntityAt(2, 2));
        assertEquals(0, actions(mage));
    }

    @Test
    void chooseRow_hitsEveryUnitInTheChosenRow() {
        Entity caster = piece(PieceAlignment.P1, 0, 0, ROW_BLAST);
        Entity a = piece(PieceAlignment.P2, 4, 0);
        Entity b = piece(PieceAlignment.P2, 4, 4);
        Entity c = piece(PieceAlignment.P2, 5, 2);

        assertEquals(Reason.OK, server.activateAbility(PieceAlignment.P1, id(caster), 0).reason());
        assertEquals(PickKind.ROW, screen.prompted.kind());
        assertEquals(Reason.OK, answer(PieceAlignment.P1, new Coord(4, 3)), "any tile of the row picks the row");
        assertEquals(2, health(a));
        assertEquals(2, health(b));
        assertEquals(3, health(c));
    }

    // --- bot-owned picks never stall ---

    @Test
    void botOwnedChooseTarget_resolvedWithoutAPrompt() {
        grid = start(Map.of(PieceAlignment.P1, BotKind.SMART));
        Entity rogue = piece(PieceAlignment.P1, 3, 2);
        Entity healthy = piece(PieceAlignment.P2, 3, 3);
        Entity weak = piece(PieceAlignment.P2, 4, 2);
        weak.getComponent(StatsComponent.class).currentHealth = 1;

        EffectNode strike = new EffectNode("ChooseTarget", Map.of(
                "candidates", "AdjacentEnemies",
                "effects", List.of(new EffectNode("Damage", Map.of("target", "$chosen", "amount", 1)))));
        EffectExecutor.execute(strike, List.of(), rogue, new ExpressionContext(), new HashMap<>());

        assertNull(screen.prompted, "nobody was asked to click");
        assertFalse(GameContext.get().getPickRouter().hasPending(PieceAlignment.P1));
        assertEquals(0, health(weak), "the bot strikes the one closest to dying");
        assertEquals(3, health(healthy));
    }

    @Test
    void botOwnedSummonPiece_placedWithoutAPrompt() {
        grid = start(Map.of(PieceAlignment.P1, BotKind.SMART));
        Entity caster = piece(PieceAlignment.P1, 3, 0);

        EffectNode summon = new EffectNode("SummonPiece", Map.of("piece", "Runner"));
        EffectExecutor.execute(summon, List.of(), caster, new ExpressionContext(), new HashMap<>());

        assertNull(screen.prompted);
        int summoned = 0;
        for (int col = 0; col < grid.getCols(); col++) if (grid.isOccupied(grid.getHomeRow(PieceAlignment.P1), col)) summoned++;
        assertEquals(1, summoned, "placed on the bot's home row");
    }

    @Test
    void humanOwnedSummonPiece_waitsForTheTile_thenPlaces() {
        Entity caster = piece(PieceAlignment.P1, 3, 0);
        EffectExecutor.execute(new EffectNode("SummonPiece", Map.of("piece", "Runner")), List.of(), caster,
                new ExpressionContext(), new HashMap<>());
        assertEquals(PickKind.TILE, screen.prompted.kind());
        assertEquals(5, screen.prompted.candidates().size(), "the whole empty home row");
        assertEquals(Reason.OK, answer(PieceAlignment.P1, new Coord(0, 3)));
        assertTrue(grid.isOccupied(0, 3));
    }
}
