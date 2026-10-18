package io.github.elderpath_crusade.client;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.*;
import io.github.elderpath_crusade.match.CardInstance;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.LegalActions;
import io.github.elderpath_crusade.match.PickRequest;
import io.github.elderpath_crusade.net.protocol.MatchView;
import io.github.elderpath_crusade.net.protocol.MatchView.PieceView;
import io.github.elderpath_crusade.net.protocol.ServerMessage;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A client's replica of the match, built purely from server messages — no simulation, no
 * LibGDX. This is what a game client renders (and what tests check against the server).
 *
 * Events update it as they happen (so a UI can animate them); StateSync then overwrites every
 * piece's public state with the server's, covering what no event carries (auras, statuses,
 * action resets). Hands, mana, turn, picks and the winner come from events/messages alone;
 * what this seat may do comes from LegalActionsUpdate (never from rules of its own).
 */
public final class ClientMatchState {
    @Getter private PieceAlignment seat;
    @Getter private int rows;
    @Getter private int cols;
    @Getter private Map<PieceAlignment, String> slots = Map.of();
    private List<Coord> terrain = List.of();
    private final Map<String, PieceView> pieces = new LinkedHashMap<>();
    private final List<CardInstance> ownHand = new ArrayList<>();
    private final List<String> opponentHand = new ArrayList<>();
    @Getter private int p1Mana;
    @Getter private int p2Mana;
    @Getter private PieceAlignment currentPlayer;
    @Getter private boolean started;
    @Getter private PickRequest pendingPick;
    @Getter private PieceAlignment winner;
    @Getter private boolean ended;
    @Getter private ServerMessage.Rejected lastRejection;
    /** The highest command seq the server has acknowledged (0 = none). */
    @Getter private long lastAckedSeq;
    @Getter private ServerMessage.Error lastError;
    /**
     * What this seat may do, as the server last said (null until the first update). The UI
     * offers exactly these — highlights, playable cards, ability bubbles, end turn.
     */
    @Getter private LegalActions legalActions;
    private Consumer<GameEvent> eventListener;

    /** Called with every event after it has been applied — e.g. for a UI to animate it. */
    public void setEventListener(Consumer<GameEvent> eventListener) {
        this.eventListener = eventListener;
    }

    public boolean isWelcomed() {
        return seat != null;
    }

    public Collection<PieceView> getPieces() {
        return Collections.unmodifiableCollection(pieces.values());
    }

    public PieceView getPiece(String id) {
        return pieces.get(id);
    }

    public PieceView getPieceAt(int row, int col) {
        for (PieceView p : pieces.values()) if (p.row() == row && p.col() == col) return p;
        return null;
    }

    public List<Coord> getTerrain() {
        return terrain;
    }

    public List<CardInstance> getOwnHand() {
        return Collections.unmodifiableList(ownHand);
    }

    public int getOpponentHandSize() {
        return opponentHand.size();
    }

    public int getMana(PieceAlignment side) {
        return side == PieceAlignment.P1 ? p1Mana : p2Mana;
    }

    public void apply(ServerMessage message) {
        if (message instanceof ServerMessage.Welcome w) {
            seat = w.seat();
            rows = w.rows();
            cols = w.cols();
            terrain = List.copyOf(w.terrain());
            slots = Map.copyOf(w.slots());
        } else if (message instanceof ServerMessage.Snapshot s) {
            applySnapshot(s.view());
        } else if (message instanceof ServerMessage.Event e) {
            applyEvent(e.event());
            if (eventListener != null) eventListener.accept(e.event());
        } else if (message instanceof ServerMessage.StateSync s) {
            pieces.clear();
            for (PieceView p : s.pieces()) pieces.put(p.id(), p);
        } else if (message instanceof ServerMessage.LegalActionsUpdate l) {
            legalActions = l.actions();
        } else if (message instanceof ServerMessage.PickRequested p) {
            pendingPick = p.request();
        } else if (message instanceof ServerMessage.PickAnswered p) {
            clearPick(p.pickId());
        } else if (message instanceof ServerMessage.PickCancelled p) {
            clearPick(p.pickId());
        } else if (message instanceof ServerMessage.Rejected r) {
            lastRejection = r;
        } else if (message instanceof ServerMessage.Ack a) {
            lastAckedSeq = Math.max(lastAckedSeq, a.seq());
        } else if (message instanceof ServerMessage.MatchEnded m) {
            winner = m.winner();
            ended = true;
            pendingPick = null;
        } else if (message instanceof ServerMessage.Error e) {
            lastError = e;
        }
    }

    /** This replica as a MatchView, comparable with the server's MatchServer.viewFor(seat). */
    public MatchView toView() {
        List<PieceView> sorted = new ArrayList<>(pieces.values());
        sorted.sort(Comparator.comparing(PieceView::id));
        return new MatchView(seat, sorted, terrain, ownHand, opponentHand, p1Mana, p2Mana,
                started ? currentPlayer : null, started, pendingPick != null ? pendingPick.pickId() : null, winner);
    }

    private void applySnapshot(MatchView view) {
        pieces.clear();
        for (PieceView p : view.pieces()) pieces.put(p.id(), p);
        terrain = view.terrain();
        ownHand.clear();
        ownHand.addAll(view.ownHand());
        opponentHand.clear();
        opponentHand.addAll(view.opponentHand());
        p1Mana = view.p1Mana();
        p2Mana = view.p2Mana();
        currentPlayer = view.currentPlayer();
        started = view.started();
        winner = view.winner();
        ended = winner != null;
        pendingPick = null; // a pending pick, if any, follows as its own PickRequested
    }

    private void applyEvent(GameEvent event) {
        if (event instanceof TurnStartedEvent e) {
            currentPlayer = e.player();
            started = true;
        } else if (event instanceof CardDrawnEvent e) {
            if (e.owner() == seat) ownHand.add(new CardInstance(e.instanceId(), e.cardName()));
            else opponentHand.add(e.instanceId());
        } else if (event instanceof CardPlayedEvent e) {
            removeFromHand(e.owner(), e.instanceId());
        } else if (event instanceof CardDiscardedEvent e) {
            for (String id : e.instanceIds()) removeFromHand(e.player(), id);
        } else if (event instanceof PieceSpawnedEvent e) {
            // Stats arrive with this tick's StateSync.
            pieces.put(e.pieceId(), new PieceView(e.pieceId(), e.owner(), e.registryKey(), e.row(), e.col(),
                    0, 0, 0, 0, false));
        } else if (event instanceof PieceMovedEvent e) {
            update(e.pieceId(), p -> withPosition(p, e.toRow(), e.toCol()));
        } else if (event instanceof PieceAttackedEvent e) {
            update(e.defenderId(), p -> withHealth(p, p.currentHealth() - e.damage()));
        } else if (event instanceof PieceDamagedEvent e) {
            update(e.pieceId(), p -> withHealth(p, p.currentHealth() - e.amount()));
        } else if (event instanceof PieceHealedEvent e) {
            update(e.pieceId(), p -> withHealth(p, Math.min(p.maxHealth(), p.currentHealth() + e.amount())));
        } else if (event instanceof PieceDiedEvent e) {
            pieces.remove(e.pieceId());
        } else if (event instanceof ManaChangedEvent e) {
            if (e.player() == PieceAlignment.P1) p1Mana = e.newMana();
            else if (e.player() == PieceAlignment.P2) p2Mana = e.newMana();
        } else if (event instanceof ActionSpentEvent e) {
            update(e.pieceId(), p -> new PieceView(p.id(), p.owner(), p.registryKey(), p.row(), p.col(),
                    p.currentHealth(), p.maxHealth(), p.damage(), e.remaining(), p.stunned()));
        } else if (event instanceof GameWonEvent e) {
            winner = e.winner();
            ended = true;
        }
        // TurnEnded, CardShuffled, PieceKilled, ActionsReset: nothing to mirror beyond StateSync.
    }

    private void removeFromHand(PieceAlignment owner, String instanceId) {
        if (owner == seat) ownHand.removeIf(c -> c.instanceId().equals(instanceId));
        else opponentHand.remove(instanceId);
    }

    private void clearPick(long pickId) {
        if (pendingPick != null && pendingPick.pickId() == pickId) pendingPick = null;
    }

    private void update(String id, java.util.function.UnaryOperator<PieceView> change) {
        PieceView p = pieces.get(id);
        if (p != null) pieces.put(id, change.apply(p));
    }

    private static PieceView withPosition(PieceView p, int row, int col) {
        return new PieceView(p.id(), p.owner(), p.registryKey(), row, col, p.currentHealth(), p.maxHealth(),
                p.damage(), p.remainingActions(), p.stunned());
    }

    private static PieceView withHealth(PieceView p, int health) {
        return new PieceView(p.id(), p.owner(), p.registryKey(), p.row(), p.col(), health, p.maxHealth(),
                p.damage(), p.remainingActions(), p.stunned());
    }
}
