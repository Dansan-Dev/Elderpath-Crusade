package io.github.elderpath_crusade.client;

import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.match.CardInstance;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.LegalActions;
import io.github.elderpath_crusade.match.PickRequest;
import io.github.elderpath_crusade.enums.PieceAlignment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Plays a seat through the client path only (ARCH-032): reads a {@link ClientMatchState} and
 * sends {@link ClientCommands}, exactly like a human's clicks. It knows no rules — every choice
 * comes from the {@link LegalActions} the server sent — so a game played this way proves a client
 * needs nothing else. Lives in {@code core} (not test code) so tests in any module, {@code
 * RunManager} integration tests and the CLI's soak mode (CLI-006) can all drive real matches,
 * including against a real child-process server through a {@link ClientMatchSession}.
 *
 * <ul>
 *   <li>{@link Strategy#GREEDY} — deterministic: answer a pick with its first candidate; for each
 *       piece in id order attack, else use an ability, else step to its most forward
 *       destination if that gains ground; then play the first playable card; then end the turn.
 *       Finishes games quickly.</li>
 *   <li>{@link Strategy#RANDOM} — fuzzing: a uniformly random legal action each step (end turn
 *       included), a random pick candidate, and sometimes cancels a cancellable pick. Reaches odd
 *       states a scripted or greedy game never would.</li>
 * </ul>
 *
 * One call sends at most one command, and never while an earlier one is unacknowledged — until
 * the server's Ack, the replica (and its LegalActions) may not show that command's effects.
 */
public final class AutoPlayer {
    public enum Strategy { GREEDY, RANDOM }

    /** Chance a RANDOM player cancels a cancellable pick instead of answering it. */
    static final double CANCEL_PICK_CHANCE = 0.2;

    private final Strategy strategy;
    private final Random random;

    public AutoPlayer(Strategy strategy, Random random) {
        this.strategy = Objects.requireNonNull(strategy);
        this.random = Objects.requireNonNull(random);
    }

    /** One step for whichever seat this session is acting as. True if a command was sent. */
    public boolean step(ClientMatchSession session) {
        return step(session.viewState(), session.pendingPick(), session, session.hasUnackedCommands());
    }

    /** One step for a single connection's seat. True if a command was sent. */
    public boolean step(MatchClient client) {
        ClientMatchState state = client.getState();
        return step(state, state.getPendingPick(), client, client.hasUnackedCommands());
    }

    boolean step(ClientMatchState state, PickRequest pick, ClientCommands commands, boolean unacked) {
        if (state == null || state.getSeat() == null || !state.isStarted() || state.isEnded() || unacked) return false;

        if (pick != null) {
            if (strategy == Strategy.RANDOM && pick.cancellable() && random.nextDouble() < CANCEL_PICK_CHANCE) {
                return sent(commands.cancelPick());
            }
            List<Coord> candidates = pick.candidates();
            Coord choice = strategy == Strategy.RANDOM ? candidates.get(random.nextInt(candidates.size())) : candidates.get(0);
            return sent(commands.answerPick(choice));
        }

        LegalActions legal = state.getLegalActions();
        if (legal == null || !legal.yourTurn() || legal.pendingPickId() != null) return false;
        return strategy == Strategy.RANDOM ? randomAction(state, legal, commands) : greedyAction(state, legal, commands);
    }

    private boolean greedyAction(ClientMatchState state, LegalActions legal, ClientCommands commands) {
        int forward = state.getSeat() == PieceAlignment.P1 ? 1 : -1;
        for (LegalActions.PieceActions p : legal.pieces()) {
            if (!p.attacks().isEmpty()) {
                Coord target = p.attacks().get(0);
                return sent(commands.moveOrAttack(p.from().row(), p.from().col(), target.row(), target.col()));
            }
            if (!p.abilities().isEmpty()) {
                return sent(commands.activateAbility(p.pieceId(), p.abilities().get(0).actionIndex()));
            }
            Coord best = null;
            for (Coord dst : p.moves()) {
                if (best == null || (dst.row() - best.row()) * forward > 0) best = dst;
            }
            if (best != null && (best.row() - p.from().row()) * forward > 0) {
                return sent(commands.moveOrAttack(p.from().row(), p.from().col(), best.row(), best.col()));
            }
        }
        if (!legal.cards().isEmpty()) return playCard(state, legal.cards().get(0), 0, commands);
        return legal.canEndTurn() && sent(commands.endTurn());
    }

    private boolean randomAction(ClientMatchState state, LegalActions legal, ClientCommands commands) {
        // Every legal option as a deferred send, then one uniform pick — end turn is one option
        // among them, so a turn always ends eventually (every other option spends something).
        List<java.util.function.BooleanSupplier> options = new ArrayList<>();
        for (LegalActions.PieceActions p : legal.pieces()) {
            for (Coord dst : p.moves()) options.add(() -> sent(commands.moveOrAttack(p.from().row(), p.from().col(), dst.row(), dst.col())));
            for (Coord dst : p.attacks()) options.add(() -> sent(commands.moveOrAttack(p.from().row(), p.from().col(), dst.row(), dst.col())));
            for (LegalActions.AbilityOption a : p.abilities()) options.add(() -> sent(commands.activateAbility(p.pieceId(), a.actionIndex())));
        }
        for (LegalActions.CardActions card : legal.cards()) {
            if (card.untargeted()) options.add(() -> playCard(state, card, -1, commands));
            for (int i = 0; i < card.targets().size(); i++) {
                int target = i;
                options.add(() -> playCard(state, card, target, commands));
            }
        }
        if (legal.canEndTurn()) options.add(() -> sent(commands.endTurn()));
        if (options.isEmpty()) return false;
        return options.get(random.nextInt(options.size())).getAsBoolean();
    }

    /** targetIndex -1 = untargeted. */
    private static boolean playCard(ClientMatchState state, LegalActions.CardActions card, int targetIndex, ClientCommands commands) {
        if (card.untargeted() || targetIndex < 0) return sent(commands.playSpell(card.instanceId(), null, null));
        Coord target = card.targets().get(targetIndex);
        if (isSpell(state, card.instanceId())) return sent(commands.playSpell(card.instanceId(), target.row(), target.col()));
        return sent(commands.playSummon(card.instanceId(), target.row(), target.col()));
    }

    private static boolean isSpell(ClientMatchState state, String instanceId) {
        for (CardInstance card : state.getOwnHand()) {
            if (card.instanceId().equals(instanceId)) return CardCatalog.get(card.cardName()) instanceof CardCatalog.Spell;
        }
        return false;
    }

    private static boolean sent(long seq) {
        return seq != ClientCommands.NOT_SENT;
    }
}
