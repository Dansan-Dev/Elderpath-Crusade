package io.github.elderpath_crusade.client;

import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.PickRequest;
import io.github.elderpath_crusade.multiplayer.net.NetworkCommand;
import io.github.elderpath_crusade.net.protocol.ClientMessage;
import io.github.elderpath_crusade.net.protocol.MessageCodec;
import io.github.elderpath_crusade.net.protocol.SeatRequest;
import io.github.elderpath_crusade.net.protocol.ServerMessage;
import io.github.elderpath_crusade.net.transport.ServerConnection;
import lombok.Getter;

/**
 * A client's side of a match: its connection to the match server plus the ClientMatchState
 * replica it keeps from what the server sends. Commands are requests — the replica only
 * changes when the server says something happened. Each command returns its seq, which a
 * ServerMessage.Rejected echoes if the server refused it.
 *
 * Not thread-safe: poll() and the commands are called from the client's own loop.
 */
public final class MatchClient {
    private final ServerConnection connection;
    @Getter private final ClientMatchState state = new ClientMatchState();
    private long nextSeq = 1;

    /** Connects and asks for a seat (the answer arrives on a later poll()). */
    public MatchClient(ServerConnection connection, SeatRequest seat, String playerName) {
        this.connection = connection;
        connection.send(new ClientMessage.Hello(MessageCodec.PROTOCOL_VERSION, seat, playerName));
    }

    /** Applies every message the server has sent since the last call. Returns how many there were. */
    public int poll() {
        int count = 0;
        ServerMessage message;
        while ((message = connection.poll()) != null) {
            state.apply(message);
            count++;
        }
        return count;
    }

    /** Whether a command this client sent hasn't been acknowledged yet (its effects may still be in flight). */
    public boolean hasUnackedCommands() {
        return state.getLastAckedSeq() < nextSeq - 1;
    }

    public boolean isConnected() {
        return connection.isOpen();
    }

    /** Moves the piece at src to dst, or attacks the enemy there. */
    public long moveOrAttack(int srcRow, int srcCol, int dstRow, int dstCol) {
        return send(new NetworkCommand.MovePlot(srcRow, srcCol, dstRow, dstCol));
    }

    public long playSummon(String instanceId, int row, int col) {
        return send(new NetworkCommand.PlaySummonCard(instanceId, row, col));
    }

    /** targetRow/targetCol null for an untargeted spell. */
    public long playSpell(String instanceId, Integer targetRow, Integer targetCol) {
        return send(new NetworkCommand.PlaySpellCard(instanceId, targetRow, targetCol));
    }

    public long activateAbility(String pieceId, int actionIndex) {
        return send(new NetworkCommand.ActivateAbility(pieceId, actionIndex));
    }

    /** Answers the pending pick with choice. */
    public long answerPick(Coord choice) {
        PickRequest pick = requirePendingPick();
        return send(new NetworkCommand.AnswerPick(pick.pickId(), false, choice.row(), choice.col()));
    }

    public long cancelPick() {
        PickRequest pick = requirePendingPick();
        return send(new NetworkCommand.AnswerPick(pick.pickId(), true, 0, 0));
    }

    public long endTurn() {
        return send(new NetworkCommand.EndTurn());
    }

    /** Leaves the match (the seat stays reserved) and closes the connection. */
    public void leave() {
        connection.send(new ClientMessage.Leave());
        connection.close();
    }

    public void close() {
        connection.close();
    }

    private PickRequest requirePendingPick() {
        PickRequest pick = state.getPendingPick();
        if (pick == null) throw new IllegalStateException("No pick pending");
        return pick;
    }

    private long send(NetworkCommand command) {
        long seq = nextSeq++;
        connection.send(new ClientMessage.Command(seq, command));
        return seq;
    }
}
