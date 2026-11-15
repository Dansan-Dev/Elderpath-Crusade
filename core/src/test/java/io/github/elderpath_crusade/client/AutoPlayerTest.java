package io.github.elderpath_crusade.client;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TurnStartedEvent;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.LegalActions;
import io.github.elderpath_crusade.match.PickKind;
import io.github.elderpath_crusade.match.PickRequest;
import io.github.elderpath_crusade.net.protocol.MessageCodec;
import io.github.elderpath_crusade.net.protocol.ServerMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** ARCH-032: AutoPlayer's decisions, from a replica alone — no server, no Gdx. */
class AutoPlayerTest {
    private ClientMatchState state;
    private final List<String> sent = new ArrayList<>();
    private final ClientCommands commands = new ClientCommands() {
        long seq;
        public long moveOrAttack(int a, int b, int c, int d) { sent.add("move " + a + "," + b + "->" + c + "," + d); return ++seq; }
        public long playSummon(String id, int r, int c) { sent.add("summon " + id + "@" + r + "," + c); return ++seq; }
        public long playSpell(String id, Integer r, Integer c) { sent.add("spell " + id); return ++seq; }
        public long activateAbility(String id, int i) { sent.add("ability " + id + "#" + i); return ++seq; }
        public long answerPick(Coord c) { sent.add("pick " + c.row() + "," + c.col()); return ++seq; }
        public long cancelPick() { sent.add("cancel"); return ++seq; }
        public long endTurn() { sent.add("end"); return ++seq; }
    };

    @BeforeEach
    void setUp() {
        state = new ClientMatchState();
        state.apply(new ServerMessage.Welcome(MessageCodec.PROTOCOL_VERSION, PieceAlignment.P1, 7, 5,
                List.of(), Map.of(PieceAlignment.P1, "HUMAN", PieceAlignment.P2, "BOT_SMART")));
        state.apply(new ServerMessage.Event(new TurnStartedEvent(PieceAlignment.P1)));
    }

    private void legal(boolean yourTurn, boolean canEnd, List<LegalActions.PieceActions> pieces) {
        state.apply(new ServerMessage.LegalActionsUpdate(
                new LegalActions(PieceAlignment.P1, yourTurn, null, canEnd, pieces, List.of())));
    }

    private static LegalActions.PieceActions piece(String id, Coord from, List<Coord> moves, List<Coord> attacks) {
        return new LegalActions.PieceActions(id, from, moves, attacks, List.of());
    }

    private boolean greedyStep(PickRequest pick, boolean unacked) {
        return new AutoPlayer(AutoPlayer.Strategy.GREEDY, new Random(0)).step(state, pick, commands, unacked);
    }

    @Test
    void greedy_attacksBeforeMoving_andMovesOnlyForward() {
        legal(true, true, List.of(piece("a", new Coord(1, 1), List.of(new Coord(0, 1), new Coord(2, 1)), List.of(new Coord(1, 2)))));
        assertTrue(greedyStep(null, false));
        assertEquals(List.of("move 1,1->1,2"), sent, "an attack beats any move");

        sent.clear();
        legal(true, true, List.of(piece("a", new Coord(1, 1), List.of(new Coord(0, 1), new Coord(2, 1)), List.of())));
        assertTrue(greedyStep(null, false));
        assertEquals(List.of("move 1,1->2,1"), sent, "P1's forward is +row");

        sent.clear();
        legal(true, true, List.of(piece("a", new Coord(1, 1), List.of(new Coord(0, 1)), List.of())));
        assertTrue(greedyStep(null, false));
        assertEquals(List.of("end"), sent, "a backward-only move isn't worth taking — end the turn");
    }

    @Test
    void neverActs_whileUnacked_offTurn_orBeforeLegalActions() {
        assertFalse(greedyStep(null, false), "no LegalActions yet");
        legal(true, true, List.of());
        assertFalse(greedyStep(null, true), "an earlier command is still unacknowledged");
        legal(false, false, List.of());
        assertFalse(greedyStep(null, false), "not our turn");
        assertTrue(sent.isEmpty());
    }

    @Test
    void picksAreAnsweredFirst_greedyTakesTheFirstCandidate() {
        legal(true, true, List.of());
        PickRequest pick = new PickRequest(3, PieceAlignment.P1, PickKind.TILE,
                List.of(new Coord(2, 2), new Coord(3, 3)), "Displace", true);
        assertTrue(greedyStep(pick, false));
        assertEquals(List.of("pick 2,2"), sent);
    }

    @Test
    void random_onlyEverSendsLegalOptions_andEventuallyCoversThemAll() {
        legal(true, true, List.of(piece("a", new Coord(1, 1), List.of(new Coord(2, 1)), List.of(new Coord(1, 2)))));
        AutoPlayer random = new AutoPlayer(AutoPlayer.Strategy.RANDOM, new Random(5));
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            sent.clear();
            assertTrue(random.step(state, null, commands, false));
            seen.add(sent.get(0));
        }
        assertEquals(Set.of("move 1,1->2,1", "move 1,1->1,2", "end"), seen);
    }

    @Test
    void random_sometimesCancelsACancellablePick_neverAnUncancellableOne() {
        AutoPlayer random = new AutoPlayer(AutoPlayer.Strategy.RANDOM, new Random(5));
        PickRequest cancellable = new PickRequest(1, PieceAlignment.P1, PickKind.PIECE, List.of(new Coord(1, 1)), "x", true);
        PickRequest mandatory = new PickRequest(2, PieceAlignment.P1, PickKind.PIECE, List.of(new Coord(1, 1)), "x", false);
        for (int i = 0; i < 100; i++) random.step(state, cancellable, commands, false);
        assertTrue(sent.contains("cancel") && sent.contains("pick 1,1"));
        sent.clear();
        for (int i = 0; i < 100; i++) random.step(state, mandatory, commands, false);
        assertFalse(sent.contains("cancel"));
    }
}
