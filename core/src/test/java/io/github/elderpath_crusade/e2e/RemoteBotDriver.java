package io.github.elderpath_crusade.e2e;

import io.github.elderpath_crusade.abilities.data.AbilityDefinition;
import io.github.elderpath_crusade.abilities.data.ActionDef;
import io.github.elderpath_crusade.abilities.data.Cost;
import io.github.elderpath_crusade.abilities.data.EffectNode;
import io.github.elderpath_crusade.client.ClientMatchState;
import io.github.elderpath_crusade.client.MatchClient;
import io.github.elderpath_crusade.data.AbilityRef;
import io.github.elderpath_crusade.data.AbilityRegistry;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.match.CardInstance;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.PickRequest;
import io.github.elderpath_crusade.net.protocol.MatchView.PieceView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A "remote bot": plays a side through the client path only — it reads its ClientMatchState
 * (plus the static piece/ability/card definitions a game client also has) and sends commands,
 * exactly like a human's clicks would. Distinct from a server-side bot seat, which plays
 * inside the simulation.
 *
 * Deliberately simple and conservative, so every command it sends is legal: answer a pending
 * pick with its first candidate; for each ready piece attack an orthogonally adjacent enemy,
 * else use an affordable ability with a valid target, else step one tile forward (or sideways
 * when blocked); summon onto a free home-row tile; end the turn when nothing is left.
 */
public final class RemoteBotDriver implements ClientDriver {

    @Override
    public boolean act(TestClient testClient) {
        ClientMatchState s = testClient.state();
        MatchClient client = testClient.client();
        PieceAlignment seat = s.getSeat();
        if (seat == null || !s.isStarted() || s.isEnded()) return false;
        // Until the server has acked the last command, the replica may not show its effects yet
        // (e.g. a sent end turn) — and once it has, a turn of ours is a new one, however short
        // the opponent's turn in between was.
        if (client.hasUnackedCommands()) return false;

        PickRequest pick = s.getPendingPick();
        if (pick != null) {
            client.answerPick(pick.candidates().get(0));
            return true;
        }
        if (s.getCurrentPlayer() != seat) return false;

        List<PieceView> ready = new ArrayList<>();
        for (PieceView p : s.getPieces()) {
            if (p.owner() == seat && p.remainingActions() > 0 && !p.stunned()) ready.add(p);
        }
        ready.sort(Comparator.comparing(PieceView::id));
        int forward = seat == PieceAlignment.P1 ? 1 : -1;
        for (PieceView p : ready) {
            for (int[] d : new int[][]{{forward, 0}, {0, -1}, {0, 1}, {-forward, 0}}) {
                PieceView target = s.getPieceAt(p.row() + d[0], p.col() + d[1]);
                if (target != null && target.owner() != seat) {
                    client.moveOrAttack(p.row(), p.col(), target.row(), target.col());
                    return true;
                }
            }
            if (useAbility(s, client, p)) return true;
            for (int[] d : new int[][]{{forward, 0}, {0, p.col() < s.getCols() / 2 ? 1 : -1}}) {
                if (isFree(s, p.row() + d[0], p.col() + d[1])) {
                    client.moveOrAttack(p.row(), p.col(), p.row() + d[0], p.col() + d[1]);
                    return true;
                }
            }
        }

        int homeRow = homeRow(s, seat);
        for (CardInstance card : s.getOwnHand()) {
            PieceDefinition def = CardCatalog.getPieceDefinition(card.cardName());
            if (def == null || def.cost() > s.getMana(seat)) continue;
            for (int col = 0; col < s.getCols(); col++) {
                if (isFree(s, homeRow, col)) {
                    client.playSummon(card.instanceId(), homeRow, col);
                    return true;
                }
            }
        }

        client.endTurn();
        return true;
    }

    /** Uses the piece's first affordable ability that has a valid target right now. */
    private static boolean useAbility(ClientMatchState s, MatchClient client, PieceView piece) {
        PieceDefinition def = PieceRegistry.get(piece.registryKey());
        if (def == null) return false;
        int actionIndex = 0;
        for (AbilityRef ref : def.abilities()) {
            AbilityDefinition ability = AbilityRegistry.get(ref.name());
            if (ability == null || ability.actions() == null || ability.actions().isEmpty()) continue;
            ActionDef action = ability.actions().get(0);
            if (manaCost(action) <= s.getMana(piece.owner()) && hasTarget(s, piece, action)) {
                client.activateAbility(piece.id(), actionIndex);
                return true;
            }
            actionIndex++;
        }
        return false;
    }

    /** Only the shapes the e2e content uses: ChooseEnemy within a range, and Self + SummonPiece. */
    private static boolean hasTarget(ClientMatchState s, PieceView piece, ActionDef action) {
        String type = action.targetSelector().type();
        if ("ChooseEnemy".equals(type)) {
            Object range = action.targetSelector().params() == null ? null : action.targetSelector().params().get("range");
            int r = range instanceof Number n ? n.intValue() : 99;
            for (PieceView other : s.getPieces()) {
                if (other.owner() != piece.owner()
                        && Math.max(Math.abs(other.row() - piece.row()), Math.abs(other.col() - piece.col())) <= r) {
                    return true;
                }
            }
            return false;
        }
        if ("Self".equals(type) && action.effects().stream().map(EffectNode::type).anyMatch("SummonPiece"::equals)) {
            int homeRow = homeRow(s, piece.owner());
            for (int col = 0; col < s.getCols(); col++) if (isFree(s, homeRow, col)) return true;
        }
        return false;
    }

    private static int manaCost(ActionDef action) {
        int mana = 0;
        for (Cost cost : action.costs()) if ("Mana".equals(cost.type())) mana += cost.amount();
        return mana;
    }

    private static int homeRow(ClientMatchState s, PieceAlignment side) {
        return side == PieceAlignment.P1 ? 0 : s.getRows() - 1;
    }

    private static boolean isFree(ClientMatchState s, int row, int col) {
        if (row < 0 || row >= s.getRows() || col < 0 || col >= s.getCols()) return false;
        if (s.getPieceAt(row, col) != null) return false;
        return !s.getTerrain().contains(new Coord(row, col));
    }
}
