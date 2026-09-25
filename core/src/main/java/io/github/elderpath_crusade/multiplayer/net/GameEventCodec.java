package io.github.elderpath_crusade.multiplayer.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.elderpath_crusade.events.*;

import java.util.Map;

/**
 * Serializes/deserializes GameEvent records for the network transport, without adding any
 * Jackson annotations to GameEvent itself — this is purely a transport-layer concern. Each
 * message is a small envelope: {"type": "<simple class name>", "payload": {...}}.
 */
public final class GameEventCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<String, Class<? extends GameEvent>> TYPES = Map.ofEntries(
            Map.entry("TurnStartedEvent", TurnStartedEvent.class),
            Map.entry("TurnEndedEvent", TurnEndedEvent.class),
            Map.entry("CardDrawnEvent", CardDrawnEvent.class),
            Map.entry("CardShuffledEvent", CardShuffledEvent.class),
            Map.entry("CardDiscardedEvent", CardDiscardedEvent.class),
            Map.entry("CardPlayedEvent", CardPlayedEvent.class),
            Map.entry("PieceSpawnedEvent", PieceSpawnedEvent.class),
            Map.entry("PieceMovedEvent", PieceMovedEvent.class),
            Map.entry("PieceAttackedEvent", PieceAttackedEvent.class),
            Map.entry("PieceDamagedEvent", PieceDamagedEvent.class),
            Map.entry("PieceHealedEvent", PieceHealedEvent.class),
            Map.entry("PieceDiedEvent", PieceDiedEvent.class),
            Map.entry("PieceKilledEvent", PieceKilledEvent.class),
            Map.entry("ManaChangedEvent", ManaChangedEvent.class),
            Map.entry("ActionsResetEvent", ActionsResetEvent.class),
            Map.entry("ActionSpentEvent", ActionSpentEvent.class),
            Map.entry("GameWonEvent", GameWonEvent.class)
    );

    private GameEventCodec() {}

    public static String encode(GameEvent event) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("type", event.getClass().getSimpleName());
            node.set("payload", MAPPER.valueToTree(event));
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode " + event, e);
        }
    }

    public static GameEvent decode(String line) {
        try {
            JsonNode node = MAPPER.readTree(line);
            String type = node.get("type").asText();
            Class<? extends GameEvent> clazz = TYPES.get(type);
            if (clazz == null) throw new IllegalArgumentException("Unknown event type: " + type);
            return MAPPER.treeToValue(node.get("payload"), clazz);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decode: " + line, e);
        }
    }
}
