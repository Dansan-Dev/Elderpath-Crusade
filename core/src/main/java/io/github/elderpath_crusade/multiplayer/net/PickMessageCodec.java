package io.github.elderpath_crusade.multiplayer.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.elderpath_crusade.match.PickRequest;

/**
 * Host → guest pick messages, in the same {"type", "payload"} envelope as GameEventCodec so
 * GameClient can tell them apart by peeking "type": PickRequested (the guest must answer with
 * NetworkCommand.AnswerPick) and PickCancelled (the host dropped it — clear the prompt).
 */
public final class PickMessageCodec {
    public static final String REQUESTED = "PickRequested";
    public static final String CANCELLED = "PickCancelled";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PickMessageCodec() {}

    public static String encodeRequested(PickRequest request) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("type", REQUESTED);
            node.set("payload", MAPPER.valueToTree(request));
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode " + request, e);
        }
    }

    public static String encodeCancelled(long pickId) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", CANCELLED);
        node.putObject("payload").put("pickId", pickId);
        return node.toString();
    }

    public static PickRequest decodeRequested(String line) {
        try {
            return MAPPER.treeToValue(MAPPER.readTree(line).get("payload"), PickRequest.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decode: " + line, e);
        }
    }

    public static long decodeCancelledPickId(String line) {
        try {
            JsonNode node = MAPPER.readTree(line);
            return node.get("payload").get("pickId").asLong();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decode: " + line, e);
        }
    }
}
