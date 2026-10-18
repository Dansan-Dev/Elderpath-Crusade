package io.github.elderpath_crusade.e2e;

import io.github.elderpath_crusade.client.ClientMatchState;
import io.github.elderpath_crusade.client.MatchClient;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.match.CardInstance;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.LegalActions;
import io.github.elderpath_crusade.match.PickRequest;

/**
 * A "remote bot": plays a side through the client path only — it reads its ClientMatchState
 * and sends commands, exactly like a human's clicks would. Distinct from a server-side bot
 * seat, which plays inside the simulation.
 *
 * It knows no rules: every choice comes from the LegalActions the server sent its seat, so
 * a whole game played this way proves a client needs nothing else. Strategy (deliberately
 * simple): answer a pending pick with its first candidate; for each piece in id order attack,
 * else use an ability, else step to its most forward destination if that gains ground; then
 * play the first playable card; then end the turn.
 */
public final class RemoteBotDriver implements ClientDriver {

    @Override
    public boolean act(TestClient testClient) {
        ClientMatchState s = testClient.state();
        MatchClient client = testClient.client();
        PieceAlignment seat = s.getSeat();
        if (seat == null || !s.isStarted() || s.isEnded()) return false;
        // Until the server has acked the last command, the replica (and its LegalActions) may not
        // show its effects yet — and once it has, a turn of ours is a new one, however short the
        // opponent's turn in between was.
        if (client.hasUnackedCommands()) return false;

        PickRequest pick = s.getPendingPick();
        if (pick != null) {
            client.answerPick(pick.candidates().get(0));
            return true;
        }
        LegalActions legal = s.getLegalActions();
        if (legal == null || !legal.yourTurn() || legal.pendingPickId() != null) return false;

        int forward = seat == PieceAlignment.P1 ? 1 : -1;
        for (LegalActions.PieceActions p : legal.pieces()) {
            if (!p.attacks().isEmpty()) {
                Coord target = p.attacks().get(0);
                client.moveOrAttack(p.from().row(), p.from().col(), target.row(), target.col());
                return true;
            }
            if (!p.abilities().isEmpty()) {
                client.activateAbility(p.pieceId(), p.abilities().get(0).actionIndex());
                return true;
            }
            Coord best = null;
            for (Coord dst : p.moves()) {
                if (best == null || (dst.row() - best.row()) * forward > 0) best = dst;
            }
            if (best != null && (best.row() - p.from().row()) * forward > 0) {
                client.moveOrAttack(p.from().row(), p.from().col(), best.row(), best.col());
                return true;
            }
        }

        for (LegalActions.CardActions card : legal.cards()) {
            if (card.untargeted()) {
                client.playSpell(card.instanceId(), null, null);
                return true;
            }
            Coord target = card.targets().get(0);
            if (isSpell(s, card.instanceId())) client.playSpell(card.instanceId(), target.row(), target.col());
            else client.playSummon(card.instanceId(), target.row(), target.col());
            return true;
        }

        if (legal.canEndTurn()) {
            client.endTurn();
            return true;
        }
        return false;
    }

    private static boolean isSpell(ClientMatchState s, String instanceId) {
        for (CardInstance card : s.getOwnHand()) {
            if (card.instanceId().equals(instanceId)) return CardCatalog.get(card.cardName()) instanceof CardCatalog.Spell;
        }
        return false;
    }
}
