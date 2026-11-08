package io.github.elderpath_crusade.cli;

import io.github.elderpath_crusade.client.ClientMatchState;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.CardInstance;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.LegalActions;
import io.github.elderpath_crusade.match.PickRequest;
import io.github.elderpath_crusade.net.protocol.MatchView.PieceView;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure functions turning a {@link ClientMatchState} (and the {@link LegalActions}/
 * {@link PickRequest} it carries) into readable text for the CLI — no session, no I/O, no
 * mutation. Every method here can be exercised with a hand-built {@code ClientMatchState},
 * exactly the way {@code ClientMatchStateTest} builds one.
 */
public final class StateRenderer {
    private static final int CELL_WIDTH = 5;

    private StateRenderer() {}

    /**
     * The board grid, one line per row (row 0 first), each cell fixed-width: a piece as its
     * {@code registryKey}'s first two letters (lowercase for P2, uppercase for P1), a trailing
     * {@code !} if stunned, {@code ##} for terrain, {@code .} for empty. If the state hasn't been
     * welcomed yet (no rows/cols known), says so instead of printing an empty grid.
     */
    public static String renderBoard(ClientMatchState state) {
        if (!state.isWelcomed()) return "(no board yet — not welcomed)";
        Set<Coord> terrain = Set.copyOf(state.getTerrain());
        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < state.getRows(); row++) {
            for (int col = 0; col < state.getCols(); col++) {
                sb.append(pad(cell(state, row, col, terrain)));
            }
            sb.append('\n');
        }
        if (sb.length() > 0) sb.setLength(sb.length() - 1); // drop trailing newline
        return sb.toString();
    }

    private static String cell(ClientMatchState state, int row, int col, Set<Coord> terrain) {
        PieceView piece = state.getPieceAt(row, col);
        if (piece != null) return pieceCode(piece);
        if (terrain.contains(new Coord(row, col))) return "##";
        return ".";
    }

    private static String pieceCode(PieceView piece) {
        String key = piece.registryKey() == null ? "" : piece.registryKey();
        String letters = key.length() >= 2 ? key.substring(0, 2) : (key + "??").substring(0, 2);
        letters = piece.owner() == PieceAlignment.P2 ? letters.toLowerCase() : letters.toUpperCase();
        return piece.stunned() ? letters + "!" : letters;
    }

    private static String pad(String code) {
        StringBuilder sb = new StringBuilder(code);
        while (sb.length() < CELL_WIDTH) sb.append(' ');
        return sb.toString();
    }

    /** One piece's detail line, for {@code show <id>}. Null if no such piece exists. */
    public static String renderPiece(ClientMatchState state, String pieceId) {
        PieceView piece = state.getPiece(pieceId);
        if (piece == null) return "no such piece: " + pieceId;
        return String.format(
                "%s (%s %s) at (%d,%d): hp %d/%d, dmg %d, actions %d%s",
                piece.id(), piece.owner(), piece.registryKey(), piece.row(), piece.col(),
                piece.currentHealth(), piece.maxHealth(), piece.damage(), piece.remainingActions(),
                piece.stunned() ? ", STUNNED" : "");
    }

    /** Own hand by name + instance id; opponent's hand as a count only — handles are opaque, see HiddenInfoFilter. */
    public static String renderHands(ClientMatchState state) {
        List<CardInstance> own = state.getOwnHand();
        String ownLine = own.isEmpty()
                ? "your hand: (empty)"
                : "your hand: " + own.stream()
                        .map(c -> c.cardName() + "#" + c.instanceId())
                        .collect(Collectors.joining(", "));
        String oppLine = "opponent's hand: " + state.getOpponentHandSize() + " card(s)";
        return ownLine + "\n" + oppLine;
    }

    /** Mana (both sides), current player, and whose turn this is from this seat's perspective. */
    public static String renderStatus(ClientMatchState state) {
        if (!state.isWelcomed()) return "(no status yet — not welcomed)";
        String mana = "mana: P1=" + state.getMana(PieceAlignment.P1) + " P2=" + state.getMana(PieceAlignment.P2);
        String turn;
        if (!state.isStarted()) {
            turn = "turn: not started";
        } else {
            PieceAlignment current = state.getCurrentPlayer();
            turn = "turn: " + current + (current == state.getSeat() ? " (your turn)" : " (not your turn)");
        }
        return mana + "\n" + turn;
    }

    /**
     * This seat's {@link LegalActions} as text: each piece's moves/attacks/abilities, each
     * playable card's targets, and whether the turn can be ended. Handles {@code null} (before
     * the first server reply) without throwing.
     */
    public static String renderLegalActions(LegalActions legal) {
        if (legal == null) return "(no legal actions yet — waiting on the server)";
        StringBuilder sb = new StringBuilder();
        sb.append("seat ").append(legal.seat())
                .append(legal.yourTurn() ? " — your turn" : " — not your turn").append('\n');
        if (legal.pendingPickId() != null) {
            sb.append("pending pick: ").append(legal.pendingPickId()).append('\n');
        }
        if (legal.pieces().isEmpty()) {
            sb.append("pieces: none\n");
        } else {
            for (LegalActions.PieceActions p : legal.pieces()) {
                sb.append("piece ").append(p.pieceId()).append(" at ").append(p.from())
                        .append(": moves=").append(p.moves())
                        .append(" attacks=").append(p.attacks());
                if (!p.abilities().isEmpty()) {
                    sb.append(" abilities=[").append(p.abilities().stream()
                            .map(StateRenderer::renderAbilityOption)
                            .collect(Collectors.joining(", "))).append(']');
                }
                sb.append('\n');
            }
        }
        if (legal.cards().isEmpty()) {
            sb.append("cards: none\n");
        } else {
            for (LegalActions.CardActions c : legal.cards()) {
                sb.append("card ").append(c.instanceId()).append(": ")
                        .append(c.untargeted() ? "untargeted" : "targets=" + c.targets())
                        .append('\n');
            }
        }
        sb.append("canEndTurn=").append(legal.canEndTurn());
        return sb.toString();
    }

    private static String renderAbilityOption(LegalActions.AbilityOption a) {
        return "#" + a.actionIndex() + " " + a.abilityId() + (a.targeted() ? " (targeted)" : "");
    }

    /**
     * The pending pick as a numbered candidate list, its kind/source, and whether it's
     * cancellable. {@code request} may be null (no pending pick) — the caller doesn't need to
     * check first, this just says so.
     */
    public static String renderPick(PickRequest request) {
        if (request == null) return "(no pending pick)";
        StringBuilder sb = new StringBuilder();
        sb.append("pick #").append(request.pickId()).append(" (").append(request.kind())
                .append(") requested by ").append(request.source())
                .append(" for ").append(request.owner())
                .append(request.cancellable() ? " [cancellable]" : " [not cancellable]").append('\n');
        List<Coord> candidates = request.candidates();
        if (candidates.isEmpty()) {
            sb.append("candidates: none");
        } else {
            for (int i = 0; i < candidates.size(); i++) {
                sb.append(i + 1).append(") ").append(candidates.get(i));
                if (i < candidates.size() - 1) sb.append('\n');
            }
        }
        return sb.toString();
    }
}
