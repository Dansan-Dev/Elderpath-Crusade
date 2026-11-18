package io.github.elderpath_crusade.lobby;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.elderpath_crusade.net.protocol.ProtocolException;

import java.util.Map;

/**
 * One JSON object per line: {"type": "<record name>", "payload": {...}}. Unknown properties are
 * rejected (Jackson's default), a missing payload is a protocol error.
 */
public final class LobbyCodec {
    /** Bumped on any incompatible change to lobby messages. */
    public static final int LOBBY_PROTOCOL_VERSION = 3;

    private static final int MAX_QUOTED_CHARS = 120;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private static final Map<String, Class<? extends LobbyClientMessage>> CLIENT_TYPES = Map.of(
            "LobbyHello", LobbyClientMessage.LobbyHello.class,
            "ListLobbies", LobbyClientMessage.ListLobbies.class,
            "CreateLobby", LobbyClientMessage.CreateLobby.class,
            "JoinLobby", LobbyClientMessage.JoinLobby.class,
            "LeaveLobby", LobbyClientMessage.LeaveLobby.class,
            "StartMatch", LobbyClientMessage.StartMatch.class,
            "RejoinMatch", LobbyClientMessage.RejoinMatch.class);

    private static final Map<String, Class<? extends LobbyServerMessage>> SERVER_TYPES = Map.of(
            "LobbyWelcome", LobbyServerMessage.LobbyWelcome.class,
            "LobbyList", LobbyServerMessage.LobbyList.class,
            "LobbyJoined", LobbyServerMessage.LobbyJoined.class,
            "LobbyUpdate", LobbyServerMessage.LobbyUpdate.class,
            "LobbyClosed", LobbyServerMessage.LobbyClosed.class,
            "MatchStarting", LobbyServerMessage.MatchStarting.class,
            "LobbyError", LobbyServerMessage.LobbyError.class);

    private LobbyCodec() {}

    public static String encode(LobbyClientMessage message) {
        return envelope(message.getClass().getSimpleName(), MAPPER.valueToTree(message));
    }

    public static String encode(LobbyServerMessage message) {
        return envelope(message.getClass().getSimpleName(), MAPPER.valueToTree(message));
    }

    public static LobbyClientMessage decodeClient(String line) {
        try {
            JsonNode node = MAPPER.readTree(line);
            Class<? extends LobbyClientMessage> clazz = CLIENT_TYPES.get(node.get("type").asText());
            if (clazz == null) throw new IllegalArgumentException("Unknown lobby message type");
            return MAPPER.treeToValue(requirePayload(node), clazz);
        } catch (Exception e) {
            throw new ProtocolException("Bad lobby message: " + quote(line), e);
        }
    }

    public static LobbyServerMessage decodeServer(String line) {
        try {
            JsonNode node = MAPPER.readTree(line);
            Class<? extends LobbyServerMessage> clazz = SERVER_TYPES.get(node.get("type").asText());
            if (clazz == null) throw new IllegalArgumentException("Unknown lobby message type");
            return MAPPER.treeToValue(requirePayload(node), clazz);
        } catch (Exception e) {
            throw new ProtocolException("Bad lobby message from server: " + quote(line), e);
        }
    }

    private static JsonNode requirePayload(JsonNode node) {
        JsonNode payload = node.get("payload");
        if (payload == null || !payload.isObject()) throw new IllegalArgumentException("Missing payload");
        return payload;
    }

    private static String quote(String line) {
        return line.length() <= MAX_QUOTED_CHARS ? line : line.substring(0, MAX_QUOTED_CHARS) + "… (" + line.length() + " chars)";
    }

    private static String envelope(String type, JsonNode payload) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", type);
        node.set("payload", payload);
        return node.toString();
    }
}
