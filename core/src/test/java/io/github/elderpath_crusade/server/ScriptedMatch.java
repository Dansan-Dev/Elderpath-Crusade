package io.github.elderpath_crusade.server;

import io.github.elderpath_crusade.client.MatchClient;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.ActionResult;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.PickKind;
import io.github.elderpath_crusade.match.PickRequest;
import io.github.elderpath_crusade.net.protocol.MatchView.PieceView;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * One fixed two-human match, played through MatchClients exactly as two game clients would:
 * summons, moves, an ability whose target is a pick answered by its owner, an attack and kill,
 * an illegal command, and a walk to the goal row. P1 plays Zappers, P2 Runners.
 * step() must deliver everything sent so far (server tick + client polls) and check both
 * replicas against the server — the transport decides how.
 */
public final class ScriptedMatch {
    private final MatchClient p1;
    private final MatchClient p2;
    private final Runnable step;

    public ScriptedMatch(MatchClient p1, MatchClient p2, Runnable step) {
        this.p1 = p1;
        this.p2 = p2;
        this.step = step;
    }

    public void play() {
        step.run(); // welcome + snapshot + match start
        assertEquals(PieceAlignment.P1, p1.getState().getSeat());
        assertEquals(PieceAlignment.P2, p2.getState().getSeat());
        assertTrue(p1.getState().isStarted());
        assertEquals(PieceAlignment.P1, p1.getState().getCurrentPlayer());
        assertEquals(3, p1.getState().getOwnHand().size());
        assertEquals(0, p2.getState().getOwnHand().size());
        assertEquals(3, p2.getState().getOpponentHandSize(), "P2 sees P1's three cards, as handles");

        // T1 P1: summon a Zapper
        p1.playSummon(MatchFixtures.firstCard(p1), 0, 0);
        step.run();
        assertEquals("Zapper", p2.getState().getPieceAt(0, 0).registryKey());
        endTurn(p1);

        // T2 P2: summon a Runner
        p2.playSummon(MatchFixtures.firstCard(p2), 6, 0);
        step.run();
        endTurn(p2);

        // T3 P1: advance. P2 trying to act on P1's turn is rejected, with its seq.
        long badSeq = p2.endTurn();
        step.run();
        assertNotNull(p2.getState().getLastRejection());
        assertEquals(badSeq, p2.getState().getLastRejection().seq());
        assertEquals(ActionResult.Reason.NOT_YOUR_TURN, p2.getState().getLastRejection().reason());
        assertNull(p1.getState().getLastRejection());
        p1.moveOrAttack(0, 0, 2, 0);
        step.run();
        endTurn(p1);

        // T4 P2: close in
        p2.moveOrAttack(6, 0, 4, 0);
        step.run();
        endTurn(p2);

        // T5 P1: Zap the Runner — the target is asked of P1 only
        String zapper = p1.getState().getPieceAt(2, 0).id();
        p1.activateAbility(zapper, 0);
        step.run();
        PickRequest pick = p1.getState().getPendingPick();
        assertNotNull(pick, "P1 is asked to choose");
        assertEquals(PickKind.PIECE, pick.kind());
        assertEquals(List.of(new Coord(4, 0)), pick.candidates());
        assertNull(p2.getState().getPendingPick(), "the opponent never sees the question");
        p1.answerPick(new Coord(4, 0));
        step.run();
        assertNull(p1.getState().getPendingPick());
        assertEquals(1, p2.getState().getPieceAt(4, 0).currentHealth(), "Zap dealt 2");
        assertEquals(1, p2.getState().getMana(PieceAlignment.P1), "2 mana - 1 for Zap");
        endTurn(p1);

        // T6 P2: step adjacent
        p2.moveOrAttack(4, 0, 3, 0);
        step.run();
        endTurn(p2);

        // T7 P1: attack and kill
        p1.moveOrAttack(2, 0, 3, 0);
        step.run();
        assertNull(p1.getState().getPieceAt(3, 0), "the Runner died");
        endTurn(p1);

        // T8-T11: walk to P2's home row
        endTurn(p2);
        p1.moveOrAttack(2, 0, 4, 0);
        step.run();
        endTurn(p1);
        endTurn(p2);
        p1.moveOrAttack(4, 0, 6, 0);
        step.run();

        for (MatchClient c : new MatchClient[]{p1, p2}) {
            assertTrue(c.getState().isEnded(), c.getState().getSeat() + " got MatchEnded");
            assertEquals(PieceAlignment.P1, c.getState().getWinner());
        }
        PieceView winner = p2.getState().getPieceAt(6, 0);
        assertNotNull(winner, "the winning move was relayed before the win");
        assertEquals(PieceAlignment.P1, winner.owner());
    }

    private void endTurn(MatchClient client) {
        PieceAlignment next = client.getState().getSeat() == PieceAlignment.P1 ? PieceAlignment.P2 : PieceAlignment.P1;
        client.endTurn();
        step.run();
        assertEquals(next, client.getState().getCurrentPlayer());
    }
}
