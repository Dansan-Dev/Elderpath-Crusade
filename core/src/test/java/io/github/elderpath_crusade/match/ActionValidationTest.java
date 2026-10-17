package io.github.elderpath_crusade.match;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.ecs.components.StunComponent;
import io.github.elderpath_crusade.ecs.factory.PieceFactory;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.match.ActionResult.Reason;
import io.github.elderpath_crusade.server.GameServer;
import io.github.elderpath_crusade.session.LocalControl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GameServer accepts nothing the UI wouldn't offer (ARCH-019): every request is validated
 * against the acting side it's given, as if it came from an untrusted client. Headless —
 * standard 7x5 board with terrain at (2,1) and (2,3), P1 to act.
 */
class ActionValidationTest {
    /** speed 2, range 1 (the default) */
    private static final PieceDefinition RUNNER = new PieceDefinition("Runner", 1, 3, 1, 2, 1, List.of());

    private GameServer server;
    private BoardGrid grid;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.createHeadless();
        PieceRegistry.register(RUNNER);
        CardCatalog.clear();
        CardCatalog.registerSummon("Runner", "Runner");
        server = GameContext.get().getGameServer();
        grid = start(GameMode.DEMO, Map.of());
    }

    @AfterEach
    void tearDown() {
        CardCatalog.clear();
        TypedEventBus.get().clear();
    }

    private BoardGrid start(GameMode mode, Map<PieceAlignment, BotKind> bots) {
        List<String> deck = new ArrayList<>(Collections.nCopies(12, "Runner"));
        BoardGrid g = MatchSetup.prepare(MatchConfig.standard(mode, deck, new ArrayList<>(deck), 7L, bots));
        MatchSetup.begin();
        return g;
    }

    private Entity piece(PieceAlignment owner, int row, int col) {
        Entity e = PieceFactory.createFromDefinition(GameContext.get().getEcsEngine(), RUNNER, owner,
                UUID.randomUUID().toString(), row, col);
        e.getComponent(StatsComponent.class).remainingActions = 1;
        grid.addEntity(row, col, e);
        return e;
    }

    private Reason move(PieceAlignment actor, int sr, int sc, int dr, int dc) {
        return server.movePlot(actor, sr, sc, dr, dc).reason();
    }

    // --- moves ---

    @Test
    void move_happyPath_movesAndSpendsAction() {
        Entity runner = piece(PieceAlignment.P1, 0, 0);
        assertEquals(Reason.OK, move(PieceAlignment.P1, 0, 0, 2, 0));
        assertSame(runner, grid.getEntityAt(2, 0));
        assertEquals(0, runner.getComponent(StatsComponent.class).remainingActions);
    }

    @Test
    void move_wrongTurn_rejected() {
        piece(PieceAlignment.P2, 6, 0);
        assertEquals(Reason.NOT_YOUR_TURN, move(PieceAlignment.P2, 6, 0, 5, 0));
        assertNotNull(grid.getEntityAt(6, 0));
    }

    @Test
    void move_noActor_rejected() {
        // a screen that controls no side right now (e.g. during a bot's turn) derives a null actor
        piece(PieceAlignment.P1, 0, 0);
        assertEquals(Reason.NOT_YOUR_TURN, move(null, 0, 0, 1, 0));
    }

    @Test
    void move_opponentsPiece_rejected() {
        piece(PieceAlignment.P2, 4, 0);
        assertEquals(Reason.NOT_YOUR_PIECE, move(PieceAlignment.P1, 4, 0, 3, 0));
        assertNotNull(grid.getEntityAt(4, 0));
    }

    @Test
    void move_fromEmptyTileOrTerrain_rejected() {
        assertEquals(Reason.NO_PIECE, move(PieceAlignment.P1, 0, 0, 1, 0));
        assertEquals(Reason.NO_PIECE, move(PieceAlignment.P1, 2, 1, 3, 1), "terrain isn't a piece");
    }

    @Test
    void move_outOfBounds_rejected() {
        piece(PieceAlignment.P1, 0, 0);
        assertEquals(Reason.OUT_OF_BOUNDS, move(PieceAlignment.P1, 0, 0, -1, 0));
        assertEquals(Reason.OUT_OF_BOUNDS, move(PieceAlignment.P1, 0, 0, 0, 5));
        assertEquals(Reason.OUT_OF_BOUNDS, move(PieceAlignment.P1, 9, 9, 0, 0));
    }

    @Test
    void move_tooFar_rejected() {
        piece(PieceAlignment.P1, 0, 0);
        assertEquals(Reason.UNREACHABLE, move(PieceAlignment.P1, 0, 0, 3, 0), "speed 2");
        assertEquals(Reason.UNREACHABLE, move(PieceAlignment.P1, 0, 0, 6, 4), "teleport across the board");
        assertNotNull(grid.getEntityAt(0, 0));
    }

    @Test
    void move_pathBlockedByTerrain_rejected() {
        // (1,1) -> (3,1) is 2 steps straight through the mountain at (2,1); around it is 4
        piece(PieceAlignment.P1, 1, 1);
        assertEquals(Reason.UNREACHABLE, move(PieceAlignment.P1, 1, 1, 3, 1));
        // the same distance down a clear column is fine
        piece(PieceAlignment.P1, 1, 0);
        assertEquals(Reason.OK, move(PieceAlignment.P1, 1, 0, 3, 0));
    }

    @Test
    void move_ontoTerrainOrFriendly_rejected() {
        piece(PieceAlignment.P1, 1, 1);
        piece(PieceAlignment.P1, 1, 2);
        assertEquals(Reason.INVALID_TARGET, move(PieceAlignment.P1, 1, 1, 2, 1), "terrain");
        assertEquals(Reason.INVALID_TARGET, move(PieceAlignment.P1, 1, 1, 1, 2), "attack own piece");
    }

    @Test
    void move_exhaustedPiece_rejected() {
        Entity runner = piece(PieceAlignment.P1, 0, 0);
        runner.getComponent(StatsComponent.class).remainingActions = 0;
        assertEquals(Reason.PIECE_EXHAUSTED, move(PieceAlignment.P1, 0, 0, 1, 0));
    }

    @Test
    void move_stunnedPiece_rejected() {
        Entity runner = piece(PieceAlignment.P1, 0, 0);
        StunComponent stun = new StunComponent();
        stun.turnsRemaining = 1;
        runner.add(stun);
        assertEquals(Reason.PIECE_STUNNED, move(PieceAlignment.P1, 0, 0, 1, 0));
    }

    // --- attacks ---

    @Test
    void attack_adjacentEnemy_accepted() {
        piece(PieceAlignment.P1, 4, 0);
        Entity enemy = piece(PieceAlignment.P2, 5, 0);
        assertEquals(Reason.OK, move(PieceAlignment.P1, 4, 0, 5, 0));
        assertTrue(enemy.getComponent(StatsComponent.class).currentHealth < 3);
    }

    @Test
    void attack_outOfRange_rejected() {
        piece(PieceAlignment.P1, 4, 0);
        Entity far = piece(PieceAlignment.P2, 6, 0);
        Entity diagonal = piece(PieceAlignment.P2, 5, 1);
        assertEquals(Reason.OUT_OF_RANGE, move(PieceAlignment.P1, 4, 0, 6, 0), "range 1");
        assertEquals(Reason.OUT_OF_RANGE, move(PieceAlignment.P1, 4, 0, 5, 1), "diagonals aren't in range");
        assertEquals(3, far.getComponent(StatsComponent.class).currentHealth);
        assertEquals(3, diagonal.getComponent(StatsComponent.class).currentHealth);
    }

    /** The server's legal destinations are exactly what the UI highlights (reachable + attackable). */
    @Test
    void validate_agreesWithHighlightedDestinations() {
        Entity runner = piece(PieceAlignment.P1, 1, 1);
        piece(PieceAlignment.P1, 1, 2);
        piece(PieceAlignment.P2, 0, 1);
        piece(PieceAlignment.P2, 3, 0);

        Set<Coord> highlighted = new HashSet<>(grid.getReachable(runner, 1, 1, 2));
        highlighted.addAll(grid.getAttackableEnemies(1, 1, PieceAlignment.P1));
        for (int r = 0; r < grid.getRows(); r++) {
            for (int c = 0; c < grid.getCols(); c++) {
                boolean legal = MoveRules.validate(PieceAlignment.P1, 1, 1, r, c).isOk();
                assertEquals(highlighted.contains(new Coord(r, c)), legal, "(" + r + "," + c + ")");
            }
        }
    }

    // --- cards (CardRulesTest covers each card rule; here: the actor decides whose hand) ---

    @Test
    void playCard_ofTheOtherPlayer_rejected() {
        String p2Card = GameContext.get().getPlayerManager().get(PieceAlignment.P2).deck.getDrawPile().get(0).instanceId();
        String p1Card = GameContext.get().getPlayerManager().get(PieceAlignment.P1).hand.getCards().get(0).instanceId();
        assertEquals(Reason.CARD_NOT_IN_HAND, server.playSummonCard(PieceAlignment.P1, p2Card, 0, 0).reason());
        assertEquals(Reason.NOT_YOUR_TURN, server.playSummonCard(PieceAlignment.P2, p1Card, 6, 0).reason(),
                "P2 can't play P1's card — not even its own turn");
        assertEquals(Reason.OK, server.playSummonCard(PieceAlignment.P1, p1Card, 0, 0).reason());
    }

    @Test
    void playCard_illegalSummonRow_rejected() {
        String card = GameContext.get().getPlayerManager().get(PieceAlignment.P1).hand.getCards().get(0).instanceId();
        assertEquals(Reason.INVALID_SUMMON_TARGET, server.playSummonCard(PieceAlignment.P1, card, 3, 0).reason());
        assertEquals(Reason.INVALID_SUMMON_TARGET, server.playSummonCard(PieceAlignment.P1, card, 6, 0).reason(),
                "the opponent's home row");
    }

    // --- end turn ---

    @Test
    void endTurn_onlyByCurrentPlayer() {
        assertEquals(Reason.NOT_YOUR_TURN, server.endTurn(PieceAlignment.P2).reason());
        assertEquals(Reason.NOT_YOUR_TURN, server.endTurn(null).reason());
        assertEquals(PieceAlignment.P1, GameContext.get().getTurnManager().getCurrentPlayer());
        assertEquals(Reason.OK, server.endTurn(PieceAlignment.P1).reason());
        assertEquals(PieceAlignment.P2, GameContext.get().getTurnManager().getCurrentPlayer());
    }

    @Test
    void endTurn_rejectedDuringHotseatHandoff() {
        grid = start(GameMode.LOCAL_MATCH, Map.of());
        assertEquals(Reason.OK, server.endTurn(PieceAlignment.P1).reason());
        assertTrue(GameContext.get().getTurnManager().isWaitingForNextPlayer());
        assertEquals(Reason.NOT_YOUR_TURN, server.endTurn(PieceAlignment.P2).reason(), "P2 hasn't taken over yet");
    }

    // --- BUG-024: a screen never acts for a bot-controlled side ---

    @Test
    void localControl_vsBot_neverControlsTheBotsSide() {
        grid = start(GameMode.DEMO, Map.of(PieceAlignment.P2, BotKind.BASIC));
        LocalControl control = GameContext.get().getLocalControl();
        assertTrue(control.controls(PieceAlignment.P1));
        assertFalse(control.controls(PieceAlignment.P2));
        assertEquals(PieceAlignment.P1, control.actingSide());

        GameContext.get().getTurnManager().endTurn(); // the bot's turn
        assertNull(control.actingSide(), "the human's screen can't act during the bot's turn");
        Entity botPiece = piece(PieceAlignment.P2, 6, 0);
        assertEquals(Reason.NOT_YOUR_TURN, move(control.actingSide(), 6, 0, 5, 0));
        assertSame(botPiece, grid.getEntityAt(6, 0));
    }

    @Test
    void localControl_hotseat_actsForWhoeversTurnItIs() {
        grid = start(GameMode.LOCAL_MATCH, Map.of());
        LocalControl control = GameContext.get().getLocalControl();
        assertEquals(PieceAlignment.P1, control.actingSide());
        server.endTurn(PieceAlignment.P1);
        GameContext.get().getTurnManager().startNextPlayerTurn();
        assertEquals(PieceAlignment.P2, control.actingSide());
    }
}
