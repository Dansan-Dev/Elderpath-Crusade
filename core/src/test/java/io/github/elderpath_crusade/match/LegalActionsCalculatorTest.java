package io.github.elderpath_crusade.match;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.abilities.data.Condition;
import io.github.elderpath_crusade.abilities.data.EffectNode;
import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.data.SpellDefinition;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.IdentityComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.server.GameServer;
import io.github.elderpath_crusade.server.MatchFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LegalActions must list exactly what the authoritative rules accept. Every check compares
 * the calculator against the validators themselves (MoveRules / AbilityRules / CardRules /
 * TurnRules) over every board square, card and ability — and the random games execute only
 * listed actions through GameServer, which must accept every one.
 */
class LegalActionsCalculatorTest {
    private static final Family PIECES = Family.all(IdentityComponent.class, PositionComponent.class,
            AlignmentComponent.class, StatsComponent.class).get();
    private static final PieceAlignment[] SIDES = {PieceAlignment.P1, PieceAlignment.P2};

    /** Records each human pick so the random player can answer it. */
    private static final class Screen implements PickResponder {
        final Map<PieceAlignment, PickRequest> prompted = new java.util.EnumMap<>(PieceAlignment.class);

        @Override
        public void onPickRequested(PickRequest request, PickRouter router) {
            prompted.put(request.owner(), request);
        }

        @Override
        public void onPickCancelled(PickRequest request) {
            prompted.remove(request.owner(), request);
        }
    }

    private Screen screen;
    private GameServer server;
    private int executed;
    /** Over all random games in this class: how often each kind of action was played. */
    private static final Map<String, Integer> PLAYED = new java.util.TreeMap<>();

    @BeforeEach
    void setUp() {
        MatchFixtures.setUpContext();
        CardCatalog.registerSpell("Bolt", new SpellDefinition("Bolt", "", 1,
                new SpellDefinition.TargetingSpec(List.of(new Condition("IsEnemy", Map.of("target", "$target")))),
                List.of(new EffectNode("Damage", Map.of("target", "$chosen", "amount", 2)))));
        CardCatalog.registerSpell("Insight", new SpellDefinition("Insight", "", 1, null,
                List.of(new EffectNode("DrawCard", Map.of()))));
        screen = new Screen();
        GameContext.get().getPickRouter().setLocalResponder(screen);
        server = GameContext.get().getGameServer();
        executed = 0;
    }

    @AfterEach
    void tearDown() {
        MatchFixtures.tearDownContext();
    }

    static Stream<Arguments> mapsAndSeeds() {
        List<Arguments> out = new ArrayList<>();
        for (String map : List.of("standard", "lopsided")) {
            for (long seed = 1; seed <= 6; seed++) out.add(Arguments.of(map, seed));
        }
        return out.stream();
    }

    @ParameterizedTest(name = "random play, {0} map, seed {1}")
    @MethodSource("mapsAndSeeds")
    void randomPlayFromLegalActions_everyListedActionIsAccepted_andNothingElseIs(String map, long seed) {
        MatchSetup.start(config(map, seed, Map.of()));
        Random rnd = new Random(seed);

        for (int step = 0; step < 800 && winner() == null; step++) {
            for (PieceAlignment side : SIDES) assertAgreesWithRules(side);
            PieceAlignment seat = GameContext.get().getTurnManager().getCurrentPlayer();
            ActionResult result = playRandomLegalAction(seat, rnd);
            assertTrue(result.isOk(), "step " + step + ": a listed action was rejected: " + result.reason());
            MatchSetup.tick(0.1f);
        }
        // A game may legitimately end early; every kind of action is required across all games (everyKindOfActionWasPlayed).
        assertTrue(executed >= 10, "the random players got to act (" + executed + " actions)");
    }

    @ParameterizedTest(name = "bot vs bot, {0} map, seed {1}")
    @MethodSource("mapsAndSeeds")
    void botVsBot_legalActionsAgreeWithTheRulesAtEveryTick(String map, long seed) {
        MatchSetup.start(config(map, seed, Map.of(PieceAlignment.P1, BotKind.SMART, PieceAlignment.P2, BotKind.BASIC)));
        for (int tick = 0; tick < 1_500 && winner() == null; tick++) {
            MatchSetup.tick(0.1f);
            for (PieceAlignment side : SIDES) assertAgreesWithRules(side);
        }
    }

    @Test
    void notYourTurn_isEmpty() {
        MatchSetup.start(config("standard", 1L, Map.of()));
        assertEquals(LegalActions.none(PieceAlignment.P2), LegalActionsCalculator.compute(PieceAlignment.P2));
        LegalActions p1 = LegalActionsCalculator.compute(PieceAlignment.P1);
        assertTrue(p1.yourTurn());
        assertTrue(p1.canEndTurn());
        assertFalse(p1.cards().isEmpty(), "P1 can summon on its first turn");
    }

    @Test
    void pendingPick_blocksEverythingButTheAnswer() {
        MatchSetup.start(config("standard", 3L, Map.of()));
        GameContext ctx = GameContext.get();
        // a P1 Conjurer on its home row, ready: Conjure asks for a home-row tile
        Entity conjurer = io.github.elderpath_crusade.ecs.factory.PieceFactory.createPiece(MatchFixtures.CONJURER,
                PieceAlignment.P1, 0, 0);
        ctx.getActiveGrid().addEntity(0, 0, conjurer);
        conjurer.getComponent(StatsComponent.class).remainingActions = 1; // past its summoning sickness
        ctx.getEcsEngine().update(0f);
        LegalActions before = LegalActionsCalculator.compute(PieceAlignment.P1);
        LegalActions.PieceActions options = before.forPiece(EntityUtils.getId(conjurer));
        assertNotNull(options);
        assertEquals(List.of(new LegalActions.AbilityOption(0, "Conjure", false)), options.abilities());

        assertTrue(server.activateAbility(PieceAlignment.P1, EntityUtils.getId(conjurer), 0).isOk());
        PickRequest pick = screen.prompted.get(PieceAlignment.P1);
        assertNotNull(pick, "Conjure asks where to summon");

        LegalActions during = LegalActionsCalculator.compute(PieceAlignment.P1);
        assertEquals(pick.pickId(), during.pendingPickId());
        assertTrue(during.pieces().isEmpty() && during.cards().isEmpty() && !during.canEndTurn());
        assertAgreesWithRules(PieceAlignment.P1);
    }

    // --- the random player ---

    private ActionResult playRandomLegalAction(PieceAlignment seat, Random rnd) {
        LegalActions legal = LegalActionsCalculator.compute(seat);
        executed++;
        if (legal.pendingPickId() != null) {
            played("pick answer");
            PickRequest pick = screen.prompted.get(seat);
            assertNotNull(pick, "the pending pick reached the screen");
            assertEquals(pick.pickId(), legal.pendingPickId());
            if (rnd.nextInt(8) == 0) return server.answerPick(seat, PickResponse.cancelled(pick.pickId()));
            Coord choice = pick.candidates().get(rnd.nextInt(pick.candidates().size()));
            return server.answerPick(seat, PickResponse.chose(pick.pickId(), choice));
        }

        List<Supplier<ActionResult>> options = new ArrayList<>();
        // By board position, not id: piece ids are random UUIDs, and the same seed must replay the same game.
        List<LegalActions.PieceActions> pieces = new ArrayList<>(legal.pieces());
        pieces.sort(java.util.Comparator.comparingInt((LegalActions.PieceActions p) -> p.from().row())
                .thenComparingInt(p -> p.from().col()));
        for (LegalActions.PieceActions p : pieces) {
            for (Coord dst : p.moves()) options.add(() -> played("move", server.movePlot(seat, p.from().row(), p.from().col(), dst.row(), dst.col())));
            for (Coord dst : p.attacks()) options.add(() -> played("attack", server.movePlot(seat, p.from().row(), p.from().col(), dst.row(), dst.col())));
            for (LegalActions.AbilityOption a : p.abilities()) options.add(() -> played("ability " + a.abilityId(), server.activateAbility(seat, p.pieceId(), a.actionIndex())));
        }
        for (LegalActions.CardActions c : legal.cards()) {
            boolean spell = CardCatalog.get(cardName(seat, c.instanceId())) instanceof CardCatalog.Spell;
            if (c.untargeted()) options.add(() -> played("untargeted spell", server.playSpellCard(seat, c.instanceId(), null, null)));
            for (Coord t : c.targets()) {
                options.add(spell
                        ? () -> played("targeted spell", server.playSpellCard(seat, c.instanceId(), t.row(), t.col()))
                        : () -> played("summon", server.playSummonCard(seat, c.instanceId(), t.row(), t.col())));
            }
        }
        if (options.isEmpty() || (legal.canEndTurn() && rnd.nextInt(6) == 0)) {
            assertTrue(legal.canEndTurn(), "nothing to do, so ending the turn must be allowed");
            return played("end turn", server.endTurn(seat));
        }
        return options.get(rnd.nextInt(options.size())).get();
    }

    private static ActionResult played(String kind, ActionResult result) {
        played(kind);
        return result;
    }

    private static void played(String kind) {
        PLAYED.merge(kind, 1, Integer::sum);
    }

    /** The random games must have exercised every kind of action, or their agreement checks prove little. */
    @org.junit.jupiter.api.AfterAll
    static void everyKindOfActionWasPlayed() {
        if (PLAYED.isEmpty()) return; // only the non-random tests ran
        System.err.println("LegalActions random play: " + PLAYED);
        for (String kind : List.of("move", "attack", "summon", "targeted spell", "untargeted spell",
                "ability Zap", "ability Conjure", "pick answer", "end turn")) {
            assertTrue(PLAYED.getOrDefault(kind, 0) > 0, "random play never did: " + kind + " " + PLAYED);
        }
    }

    // --- the agreement check ---

    /** LegalActions for side == exactly what the validators accept, over every square, card and ability. */
    private void assertAgreesWithRules(PieceAlignment side) {
        GameContext ctx = GameContext.get();
        LegalActions legal = LegalActionsCalculator.compute(side);
        boolean pending = ctx.getPickRouter().hasPending(side);
        assertEquals(TurnRules.validateEndTurn(side).isOk() && !pending, legal.canEndTurn(), "canEndTurn");
        if (pending) {
            assertTrue(legal.pieces().isEmpty() && legal.cards().isEmpty(), "a pending pick blocks every action");
            return; // GameServer rejects everything but the answer with PICK_PENDING
        }
        BoardGrid grid = ctx.getActiveGrid();

        ImmutableArray<Entity> pieces = ctx.getEcsEngine().getEntitiesFor(PIECES);
        List<String> own = new ArrayList<>();
        for (int i = 0; i < pieces.size(); i++) {
            Entity piece = pieces.get(i);
            if (EntityUtils.getAlignment(piece) != side) continue;
            String id = EntityUtils.getId(piece);
            own.add(id);
            LegalActions.PieceActions listed = legal.forPiece(id);
            int row = EntityUtils.getRow(piece), col = EntityUtils.getCol(piece);
            boolean onBoard = grid.isInBounds(row, col) && grid.getEntityAt(row, col) == piece;
            for (int r = 0; r < grid.getRows(); r++) {
                for (int c = 0; c < grid.getCols(); c++) {
                    Coord dst = new Coord(r, c);
                    boolean accepted = onBoard && MoveRules.validate(side, row, col, r, c).isOk();
                    boolean asMove = listed != null && listed.moves().contains(dst);
                    boolean asAttack = listed != null && listed.attacks().contains(dst);
                    assertEquals(accepted, asMove || asAttack, id + " " + new Coord(row, col) + " -> " + dst);
                    if (asMove) assertFalse(grid.isOccupied(r, c), "moves go to empty squares");
                    if (asAttack) assertTrue(grid.isOccupied(r, c), "attacks target occupied squares");
                }
            }
            List<?> actionable = AbilityRules.actionableAbilities(piece);
            for (int index = 0; index < actionable.size(); index++) {
                final int ix = index;
                boolean accepted = AbilityRules.validate(side, id, index).isOk();
                boolean shown = listed != null && listed.abilities().stream().anyMatch(a -> a.actionIndex() == ix);
                assertEquals(accepted, shown, id + " ability " + index);
            }
        }
        for (LegalActions.PieceActions p : legal.pieces()) assertTrue(own.contains(p.pieceId()), "only own pieces");

        CardRules rules = ctx.getCardRules();
        List<CardInstance> hand = ctx.getPlayerManager().get(side).hand.getCards();
        for (CardInstance card : hand) {
            LegalActions.CardActions listed = legal.forCard(card.instanceId());
            boolean summon = CardCatalog.get(card.cardName()) instanceof CardCatalog.Summon;
            for (int r = 0; r < grid.getRows(); r++) {
                for (int c = 0; c < grid.getCols(); c++) {
                    boolean accepted = summon
                            ? rules.validateSummon(side, card.instanceId(), r, c).isOk()
                            : rules.validateSpell(side, card.instanceId(), r, c).isOk();
                    boolean shown = listed != null && listed.targets().contains(new Coord(r, c));
                    assertEquals(accepted, shown, card + " at " + new Coord(r, c));
                }
            }
            boolean untargetedOk = !summon && rules.validateSpell(side, card.instanceId(), null, null).isOk();
            assertEquals(untargetedOk, listed != null && listed.untargeted(), card + " untargeted");
        }
        for (LegalActions.CardActions c : legal.cards()) {
            assertTrue(hand.stream().anyMatch(h -> h.instanceId().equals(c.instanceId())), "only cards in hand");
        }
    }

    // --- helpers ---

    private static MatchConfig config(String map, long seed, Map<PieceAlignment, BotKind> bots) {
        List<String> deck = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            deck.add("Runner");
            deck.add("Zapper");
            deck.add("Conjurer");
            deck.add("Bolt");
            deck.add("Insight");
        }
        return "standard".equals(map)
                ? MatchConfig.standard(GameMode.ONLINE_MATCH, deck, deck, seed, bots)
                : MatchFixtures.lopsidedConfig(deck, deck, seed, bots);
    }

    private static String cardName(PieceAlignment seat, String instanceId) {
        CardInstance card = GameContext.get().getPlayerManager().get(seat).hand.find(instanceId);
        return card == null ? null : card.cardName();
    }

    private static PieceAlignment winner() {
        return GameContext.get().getWinConditionManager().getWinner();
    }
}
