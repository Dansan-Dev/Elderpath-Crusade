package io.github.elderpath_crusade.net.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.elderpath_crusade.multiplayer.net.GameEventCodec;
import io.github.elderpath_crusade.multiplayer.net.NetworkCommandCodec;

import java.util.Map;

/**
 * One JSON object per line: {"type": "<message record name>", "payload": {...}}. Commands and
 * events nest their own {"type", "payload"} envelopes (NetworkCommandCodec/GameEventCodec).
 * Unknown properties are rejected, so a client can't smuggle fields in (e.g. an acting side).
 */
public final class MessageCodec {
    /** Bumped on any incompatible change to messages, commands or events. */
    public static final int PROTOCOL_VERSION = 2;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private static final Map<String, Class<? extends ClientMessage>> CLIENT_TYPES = Map.of(
            "Hello", ClientMessage.Hello.class,
            "Leave", ClientMessage.Leave.class
    );

    private static final Map<String, Class<? extends ServerMessage>> SERVER_TYPES = Map.ofEntries(
            Map.entry("Welcome", ServerMessage.Welcome.class),
            Map.entry("Snapshot", ServerMessage.Snapshot.class),
            Map.entry("StateSync", ServerMessage.StateSync.class),
            Map.entry("LegalActionsUpdate", ServerMessage.LegalActionsUpdate.class),
            Map.entry("PickRequested", ServerMessage.PickRequested.class),
            Map.entry("PickAnswered", ServerMessage.PickAnswered.class),
            Map.entry("PickCancelled", ServerMessage.PickCancelled.class),
            Map.entry("Rejected", ServerMessage.Rejected.class),
            Map.entry("Ack", ServerMessage.Ack.class),
            Map.entry("MatchEnded", ServerMessage.MatchEnded.class),
            Map.entry("Error", ServerMessage.Error.class)
    );

    private MessageCodec() {}

    public static String encode(ClientMessage message) {
        ObjectNode payload;
        if (message instanceof ClientMessage.Command c) {
            payload = MAPPER.createObjectNode();
            payload.put("seq", c.seq());
            payload.set("command", NetworkCommandCodec.toTree(c.command()));
        } else {
            payload = MAPPER.valueToTree(message);
        }
        return envelope(message.getClass().getSimpleName(), payload);
    }

    public static String encode(ServerMessage message) {
        ObjectNode payload;
        if (message instanceof ServerMessage.Event e) {
            payload = MAPPER.createObjectNode();
            payload.set("event", GameEventCodec.toTree(e.event()));
        } else {
            payload = MAPPER.valueToTree(message);
        }
        return envelope(message.getClass().getSimpleName(), payload);
    }

    public static ClientMessage decodeClient(String line) {
        try {
            JsonNode node = MAPPER.readTree(line);
            String type = node.get("type").asText();
            JsonNode payload = node.get("payload");
            if ("Command".equals(type)) {
                return new ClientMessage.Command(payload.get("seq").asLong(),
                        NetworkCommandCodec.fromTree(payload.get("command")));
            }
            Class<? extends ClientMessage> clazz = CLIENT_TYPES.get(type);
            if (clazz == null) throw new IllegalArgumentException("Unknown client message type: " + type);
            return MAPPER.treeToValue(payload, clazz);
        } catch (Exception e) {
            throw new ProtocolException("Bad client message: " + line, e);
        }
    }

    public static ServerMessage decodeServer(String line) {
        try {
            JsonNode node = MAPPER.readTree(line);
            String type = node.get("type").asText();
            JsonNode payload = node.get("payload");
            if ("Event".equals(type)) {
                return new ServerMessage.Event(GameEventCodec.fromTree(payload.get("event")));
            }
            Class<? extends ServerMessage> clazz = SERVER_TYPES.get(type);
            if (clazz == null) throw new IllegalArgumentException("Unknown server message type: " + type);
            return MAPPER.treeToValue(payload, clazz);
        } catch (Exception e) {
            throw new ProtocolException("Bad server message: " + line, e);
        }
    }

    private static String envelope(String type, JsonNode payload) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", type);
        node.set("payload", payload);
        return node.toString();
    }
}
