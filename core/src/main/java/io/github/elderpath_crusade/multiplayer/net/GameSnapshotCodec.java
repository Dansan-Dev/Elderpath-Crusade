package io.github.elderpath_crusade.multiplayer.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Same envelope shape as GameEventCodec ({"type": "GameSnapshot", "payload": {...}}) so
 * GameClient can peek the "type" field to tell a snapshot apart from a regular relayed event
 * on the wire, without needing a separate message-kind byte/header.
 */
public final class GameSnapshotCodec {
    public static final String TYPE = "GameSnapshot";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GameSnapshotCodec() {}

    public static String encode(GameSnapshot snapshot) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("type", TYPE);
            node.set("payload", MAPPER.valueToTree(snapshot));
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode " + snapshot, e);
        }
    }

    public static GameSnapshot decode(String line) {
        try {
            JsonNode node = MAPPER.readTree(line);
            return MAPPER.treeToValue(node.get("payload"), GameSnapshot.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decode: " + line, e);
        }
    }
}
