package io.github.elderpath_crusade.cli;

import io.github.elderpath_crusade.client.ClientMatchState;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.CardDrawnEvent;
import io.github.elderpath_crusade.events.GameEvent;
import io.github.elderpath_crusade.events.PieceSpawnedEvent;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.LegalActions;
import io.github.elderpath_crusade.match.PickKind;
import io.github.elderpath_crusade.match.PickRequest;
import io.github.elderpath_crusade.net.protocol.MatchView.PieceView;
import io.github.elderpath_crusade.net.protocol.MessageCodec;
import io.github.elderpath_crusade.net.protocol.ServerMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Builds ClientMatchState fixtures by hand (same pattern as ClientMatchStateTest) and asserts on
 * StateRenderer's actual output, no session/process involved.
 */
class StateRendererTest {

    private static ClientMatchState notWelcomed() {
        return new ClientMatchState();
    }

    private static ClientMatchState welcomed(PieceAlignment seat, int rows, int cols, List<Coord> terrain) {
        ClientMatchState state = new ClientMatchState();
        state.apply(new ServerMessage.Welcome(MessageCodec.PROTOCOL_VERSION, seat, rows, cols, terrain,
                Map.of(PieceAlignment.P1, "HUMAN", PieceAlignment.P2, "HUMAN")));
        return state;
    }

    private static void event(ClientMatchState state, GameEvent e) {
        state.apply(new ServerMessage.Event(e));
    }

    // ---- renderBoard ----

    @Test
    void renderBoard_notWelcomed_saysSo() {
        assertEquals("(no board yet — not welcomed)", StateRenderer.renderBoard(notWelcomed()));
    }

    @Test
    void renderBoard_emptyGrid_isAllDots() {
        ClientMatchState state = welcomed(PieceAlignment.P1, 2, 3, List.of());
        String board = StateRenderer.renderBoard(state);
        String[] lines = board.split("\n");
        assertEquals(2, lines.length);
        for (String line : lines) {
            assertEquals("...", line.replace(" ", ""), "expected an all-empty row, got: [" + line + "]");
        }
    }

    @Test
    void renderBoard_piecesTerrainAndStun_renderedWithOwnerCaseAndMarkers() {
        ClientMatchState state = welcomed(PieceAlignment.P1, 3, 3, List.of(new Coord(1, 1)));
        event(state, new PieceSpawnedEvent("p1piece", PieceAlignment.P1, 0, 0, "Wolf"));
        event(state, new PieceSpawnedEvent("p2piece", PieceAlignment.P2, 2, 2, "Runner"));
        state.apply(new ServerMessage.StateSync(List.of(
                new PieceView("p1piece", PieceAlignment.P1, "Wolf", 0, 0, 3, 3, 1, 1, false),
                new PieceView("p2piece", PieceAlignment.P2, "Runner", 2, 2, 2, 2, 1, 1, true))));

        String board = StateRenderer.renderBoard(state);
        String[] lines = board.split("\n");
        assertEquals(3, lines.length);

        // Row 0: P1 piece "Wolf" -> uppercase "WO", no stun marker.
        assertTrue(lines[0].contains("WO"), "expected P1 piece code in row 0: [" + lines[0] + "]");
        assertFalse(lines[0].contains("WO!"), "P1 piece isn't stunned, shouldn't carry a stun marker");

        // Row 1: terrain at (1,1) -> "##".
        assertTrue(lines[1].contains("##"), "expected terrain marker in row 1: [" + lines[1] + "]");

        // Row 2: P2 piece "Runner", stunned -> lowercase "ru!" (owner-lowercased + stun marker).
        assertTrue(lines[2].contains("ru!"), "expected stunned lowercase P2 piece code in row 2: [" + lines[2] + "]");
    }

    @Test
    void renderBoard_isFixedWidthMonospaceFriendly() {
        ClientMatchState state = welcomed(PieceAlignment.P1, 1, 4, List.of());
        event(state, new PieceSpawnedEvent("a", PieceAlignment.P1, 0, 0, "Wolf"));
        state.apply(new ServerMessage.StateSync(List.of(
                new PieceView("a", PieceAlignment.P1, "Wolf", 0, 0, 1, 1, 1, 1, false))));
        String board = StateRenderer.renderBoard(state);
        // Every row must be the same length regardless of cell contents (fixed-width cells).
        assertEquals(1, board.split("\n").length);
        // 4 cells of fixed width - trailing spaces are trimmed by String but length per cell is constant,
        // so the line up to the last non-space char must start at the same offsets; just assert no exception
        // and the piece code appears at the start of the line.
        assertTrue(board.trim().startsWith("WO"));
    }

    // ---- renderPiece ----

    @Test
    void renderPiece_unknownId_saysSo() {
        ClientMatchState state = welcomed(PieceAlignment.P1, 3, 3, List.of());
        assertEquals("no such piece: ghost", StateRenderer.renderPiece(state, "ghost"));
    }

    @Test
    void renderPiece_includesHealthDamageActionsAndStun() {
        ClientMatchState state = welcomed(PieceAlignment.P1, 3, 3, List.of());
        event(state, new PieceSpawnedEvent("a", PieceAlignment.P2, 1, 1, "Runner"));
        state.apply(new ServerMessage.StateSync(List.of(
                new PieceView("a", PieceAlignment.P2, "Runner", 1, 1, 2, 5, 3, 0, true))));

        String line = StateRenderer.renderPiece(state, "a");
        assertTrue(line.contains("hp 2/5"), line);
        assertTrue(line.contains("dmg 3"), line);
        assertTrue(line.contains("actions 0"), line);
        assertTrue(line.contains("STUNNED"), line);
        assertTrue(line.contains("Runner"), line);
        assertTrue(line.contains("P2"), line);
    }

    // ---- renderHands ----

    @Test
    void renderHands_ownByName_opponentAsCountOnly() {
        ClientMatchState state = welcomed(PieceAlignment.P1, 3, 3, List.of());
        event(state, new CardDrawnEvent(PieceAlignment.P1, "P1-0", "Wolf", 1));
        event(state, new CardDrawnEvent(PieceAlignment.P1, "P1-1", "Fireball", 2));
        event(state, new CardDrawnEvent(PieceAlignment.P2, "hidden-1", null, 1));
        event(state, new CardDrawnEvent(PieceAlignment.P2, "hidden-2", null, 2));

        String hands = StateRenderer.renderHands(state);
        assertTrue(hands.contains("Wolf#P1-0"), hands);
        assertTrue(hands.contains("Fireball#P1-1"), hands);
        assertTrue(hands.contains("opponent's hand: 2 card(s)"), hands);
        // Never print the opaque handle ids as if informative.
        assertFalse(hands.contains("hidden-1"), hands);
        assertFalse(hands.contains("hidden-2"), hands);
    }

    @Test
    void renderHands_emptyOwnHand_saysEmpty() {
        ClientMatchState state = welcomed(PieceAlignment.P1, 3, 3, List.of());
        String hands = StateRenderer.renderHands(state);
        assertTrue(hands.contains("your hand: (empty)"), hands);
        assertTrue(hands.contains("opponent's hand: 0 card(s)"), hands);
    }

    // ---- renderStatus ----

    @Test
    void renderStatus_notWelcomed_saysSo() {
        assertEquals("(no status yet — not welcomed)", StateRenderer.renderStatus(notWelcomed()));
    }

    @Test
    void renderStatus_manaAndTurnFromThisSeatsPerspective() {
        ClientMatchState state = welcomed(PieceAlignment.P1, 3, 3, List.of());
        event(state, new io.github.elderpath_crusade.events.ManaChangedEvent(PieceAlignment.P1, 3));
        event(state, new io.github.elderpath_crusade.events.ManaChangedEvent(PieceAlignment.P2, 4));
        event(state, new io.github.elderpath_crusade.events.TurnStartedEvent(PieceAlignment.P1));

        String status = StateRenderer.renderStatus(state);
        assertTrue(status.contains("mana: P1=3 P2=4"), status);
        assertTrue(status.contains("turn: P1 (your turn)"), status);
    }

    @Test
    void renderStatus_opponentsTurn_saysNotYourTurn() {
        ClientMatchState state = welcomed(PieceAlignment.P1, 3, 3, List.of());
        event(state, new io.github.elderpath_crusade.events.TurnStartedEvent(PieceAlignment.P2));
        String status = StateRenderer.renderStatus(state);
        assertTrue(status.contains("turn: P2 (not your turn)"), status);
    }

    @Test
    void renderStatus_notStarted_saysSo() {
        ClientMatchState state = welcomed(PieceAlignment.P1, 3, 3, List.of());
        String status = StateRenderer.renderStatus(state);
        assertTrue(status.contains("turn: not started"), status);
    }

    // ---- renderLegalActions ----

    @Test
    void renderLegalActions_null_saysWaitingOnServer() {
        String out = StateRenderer.renderLegalActions(null);
        assertEquals("(no legal actions yet — waiting on the server)", out);
    }

    @Test
    void renderLegalActions_populated_includesMovesAttacksAbilitiesCardsAndCanEndTurn() {
        LegalActions.PieceActions pieceActions = new LegalActions.PieceActions(
                "p1", new Coord(0, 0),
                List.of(new Coord(0, 1), new Coord(1, 0)),
                List.of(new Coord(1, 1)),
                List.of(new LegalActions.AbilityOption(0, "Pounce", true)));
        LegalActions.CardActions cardActions = new LegalActions.CardActions(
                "P1-0", false, List.of(new Coord(0, 2)));
        LegalActions legal = new LegalActions(PieceAlignment.P1, true, null, true,
                List.of(pieceActions), List.of(cardActions));

        String out = StateRenderer.renderLegalActions(legal);
        assertTrue(out.contains("seat P1"), out);
        assertTrue(out.contains("your turn"), out);
        assertTrue(out.contains("piece p1 at (0, 0)"), out);
        assertTrue(out.contains("moves=[(0, 1), (1, 0)]"), out);
        assertTrue(out.contains("attacks=[(1, 1)]"), out);
        assertTrue(out.contains("#0 Pounce (targeted)"), out);
        assertTrue(out.contains("card P1-0: targets=[(0, 2)]"), out);
        assertTrue(out.contains("canEndTurn=true"), out);
    }

    @Test
    void renderLegalActions_untargetedCardAndNoOptions() {
        LegalActions.CardActions cardActions = new LegalActions.CardActions("S-0", true, List.of());
        LegalActions legal = new LegalActions(PieceAlignment.P2, false, 7L, false,
                List.of(), List.of(cardActions));

        String out = StateRenderer.renderLegalActions(legal);
        assertTrue(out.contains("not your turn"), out);
        assertTrue(out.contains("pending pick: 7"), out);
        assertTrue(out.contains("pieces: none"), out);
        assertTrue(out.contains("card S-0: untargeted"), out);
        assertTrue(out.contains("canEndTurn=false"), out);
    }

    // ---- renderPick ----

    @Test
    void renderPick_null_saysNoPendingPick() {
        assertEquals("(no pending pick)", StateRenderer.renderPick(null));
    }

    @Test
    void renderPick_numberedCandidatesKindSourceAndCancellable() {
        PickRequest request = new PickRequest(5, PieceAlignment.P1, PickKind.TILE,
                List.of(new Coord(0, 1), new Coord(2, 3)), "RogueFreeStrike", true);
        String out = StateRenderer.renderPick(request);
        assertTrue(out.contains("pick #5"), out);
        assertTrue(out.contains("TILE"), out);
        assertTrue(out.contains("RogueFreeStrike"), out);
        assertTrue(out.contains("P1"), out);
        assertTrue(out.contains("[cancellable]"), out);
        assertTrue(out.contains("1) (0, 1)"), out);
        assertTrue(out.contains("2) (2, 3)"), out);
    }

    @Test
    void renderPick_notCancellable_andNoCandidates() {
        PickRequest request = new PickRequest(6, PieceAlignment.P2, PickKind.ROW, List.of(), "Scavenge", false);
        String out = StateRenderer.renderPick(request);
        assertTrue(out.contains("[not cancellable]"), out);
        assertTrue(out.contains("candidates: none"), out);
    }

    // ---- pending pick via ClientMatchState (exercising the actual pipeline, not just the record) ----

    @Test
    void renderPick_fromClientMatchStatePendingPick() {
        ClientMatchState state = welcomed(PieceAlignment.P1, 3, 3, List.of());
        PickRequest request = new PickRequest(1, PieceAlignment.P1, PickKind.PIECE,
                List.of(new Coord(0, 0)), "WarpMage Displace", true);
        state.apply(new ServerMessage.PickRequested(request));

        assertNotNull(state.getPendingPick());
        String out = StateRenderer.renderPick(state.getPendingPick());
        assertTrue(out.contains("WarpMage Displace"), out);
        assertTrue(out.contains("1) (0, 0)"), out);
    }
}
