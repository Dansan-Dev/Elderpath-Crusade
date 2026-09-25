package io.github.elderpath_crusade.multiplayer.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/** Same envelope-based approach as GameEventCodec, for the (much smaller) NetworkCommand set. */
public final class NetworkCommandCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<String, Class<? extends NetworkCommand>> TYPES = Map.of(
            "MovePlot", NetworkCommand.MovePlot.class,
            "PlaySummonCard", NetworkCommand.PlaySummonCard.class,
            "PlaySpellCard", NetworkCommand.PlaySpellCard.class,
            "EndTurn", NetworkCommand.EndTurn.class
    );

    private NetworkCommandCodec() {}

    public static String encode(NetworkCommand command) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("type", command.getClass().getSimpleName());
            node.set("payload", MAPPER.valueToTree(command));
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode " + command, e);
        }
    }

    public static NetworkCommand decode(String line) {
        try {
            JsonNode node = MAPPER.readTree(line);
            String type = node.get("type").asText();
            Class<? extends NetworkCommand> clazz = TYPES.get(type);
            if (clazz == null) throw new IllegalArgumentException("Unknown command type: " + type);
            return MAPPER.treeToValue(node.get("payload"), clazz);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decode: " + line, e);
        }
    }
}
