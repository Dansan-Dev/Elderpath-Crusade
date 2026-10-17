package io.github.elderpath_crusade.net.protocol;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.CardDrawnEvent;
import io.github.elderpath_crusade.events.PieceMovedEvent;
import io.github.elderpath_crusade.events.PieceSpawnedEvent;
import io.github.elderpath_crusade.match.ActionResult;
import io.github.elderpath_crusade.match.CardInstance;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.PickKind;
import io.github.elderpath_crusade.match.PickRequest;
import io.github.elderpath_crusade.multiplayer.net.NetworkCommand;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageCodecTest {
    private static final MatchView.PieceView PIECE = new MatchView.PieceView("id-1", PieceAlignment.P2, "Runner",
            4, 0, 1, 3, 1, 0, true);

    @Test
    void everyClientMessage_roundTrips() {
        List<ClientMessage> messages = List.of(
                new ClientMessage.Hello(MessageCodec.PROTOCOL_VERSION, SeatRequest.ANY, "ann"),
                new ClientMessage.Hello(MessageCodec.PROTOCOL_VERSION, SeatRequest.P2, null),
                new ClientMessage.Command(7, new NetworkCommand.MovePlot(0, 1, 2, 1)),
                new ClientMessage.Command(8, new NetworkCommand.PlaySummonCard("P1-3", 0, 2)),
                new ClientMessage.Command(9, new NetworkCommand.PlaySpellCard("P1-4", null, null)),
                new ClientMessage.Command(10, new NetworkCommand.EndTurn()),
                new ClientMessage.Command(11, new NetworkCommand.ActivateAbility("id-1", 0)),
                new ClientMessage.Command(12, new NetworkCommand.AnswerPick(5, false, 4, 0)),
                new ClientMessage.Leave());
        for (ClientMessage m : messages) assertEquals(m, MessageCodec.decodeClient(MessageCodec.encode(m)));
    }

    @Test
    void everyServerMessage_roundTrips() {
        MatchView view = new MatchView(PieceAlignment.P1, List.of(PIECE), List.of(new Coord(2, 1)),
                List.of(new CardInstance("P1-0", "Wolf")), List.of("hidden-1"), 2, 1, PieceAlignment.P2, true, 5L, null);
        List<ServerMessage> messages = List.of(
                new ServerMessage.Welcome(MessageCodec.PROTOCOL_VERSION, PieceAlignment.P1, 7, 5,
                        List.of(new Coord(2, 1), new Coord(2, 3)), Map.of(PieceAlignment.P1, "HUMAN", PieceAlignment.P2, "BOT_SMART")),
                new ServerMessage.Snapshot(view),
                new ServerMessage.Event(new CardDrawnEvent(PieceAlignment.P2, "hidden-2", null, 3)),
                new ServerMessage.Event(new PieceSpawnedEvent("id-1", PieceAlignment.P2, 6, 0, "Runner")),
                new ServerMessage.Event(new PieceMovedEvent("id-1", PieceAlignment.P2, 6, 0, 4, 0,
                        PieceMovedEvent.MovementType.ACTIVE, "MovementSystem")),
                new ServerMessage.StateSync(List.of(PIECE)),
                new ServerMessage.PickRequested(new PickRequest(5, PieceAlignment.P1, PickKind.PIECE,
                        List.of(new Coord(4, 0)), "Zap", true)),
                new ServerMessage.PickAnswered(5),
                new ServerMessage.PickCancelled(5),
                new ServerMessage.Rejected(9, ActionResult.Reason.NOT_YOUR_TURN),
                new ServerMessage.MatchEnded(PieceAlignment.P1),
                new ServerMessage.Error(ServerMessage.Error.Code.MATCH_FULL, "full"));
        for (ServerMessage m : messages) assertEquals(m, MessageCodec.decodeServer(MessageCodec.encode(m)));
    }

    @Test
    void smuggledFields_andUnknownTypes_areRejected() {
        String smuggled = MessageCodec.encode(new ClientMessage.Command(1, new NetworkCommand.EndTurn()))
                .replace("\"payload\":{}", "\"payload\":{\"alignment\":\"P1\"}");
        assertThrows(ProtocolException.class, () -> MessageCodec.decodeClient(smuggled));
        assertThrows(ProtocolException.class, () -> MessageCodec.decodeClient("{\"type\":\"Nope\",\"payload\":{}}"));
        assertThrows(ProtocolException.class, () -> MessageCodec.decodeServer("not json"));
    }
}
