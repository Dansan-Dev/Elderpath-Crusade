package io.github.elderpath_crusade.multiplayer.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/** Same envelope-based approach as GameEventCodec, for the (much smaller) NetworkCommand set. */
public final class NetworkCommandCodec {
    // EndTurn has no fields; unknown properties stay rejected (the default), so a payload
    // can't smuggle in e.g. an acting side.
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private static final Map<String, Class<? extends NetworkCommand>> TYPES = Map.of(
            "MovePlot", NetworkCommand.MovePlot.class,
            "PlaySummonCard", NetworkCommand.PlaySummonCard.class,
            "PlaySpellCard", NetworkCommand.PlaySpellCard.class,
            "EndTurn", NetworkCommand.EndTurn.class,
            "ActivateAbility", NetworkCommand.ActivateAbility.class,
            "AnswerPick", NetworkCommand.AnswerPick.class
    );

    private NetworkCommandCodec() {}

    public static String encode(NetworkCommand command) {
        try {
            return MAPPER.writeValueAsString(toTree(command));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode " + command, e);
        }
    }

    public static NetworkCommand decode(String line) {
        try {
            return fromTree(MAPPER.readTree(line));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decode: " + line, e);
        }
    }

    /** The {"type", "payload"} envelope as a tree — for embedding a command in a larger message. */
    public static ObjectNode toTree(NetworkCommand command) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", command.getClass().getSimpleName());
        node.set("payload", MAPPER.valueToTree(command));
        return node;
    }

    public static NetworkCommand fromTree(JsonNode node) {
        try {
            String type = node.get("type").asText();
            Class<? extends NetworkCommand> clazz = TYPES.get(type);
            if (clazz == null) throw new IllegalArgumentException("Unknown command type: " + type);
            return MAPPER.treeToValue(node.get("payload"), clazz);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decode command: " + node, e);
        }
    }
}
