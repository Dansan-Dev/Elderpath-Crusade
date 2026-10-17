package io.github.elderpath_crusade.multiplayer.net;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.CardDiscardedEvent;
import io.github.elderpath_crusade.events.CardDrawnEvent;
import io.github.elderpath_crusade.events.CardPlayedEvent;
import io.github.elderpath_crusade.events.GameEvent;
import io.github.elderpath_crusade.match.CardInstance;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Round-trips the card-identity-carrying messages (events, commands, snapshot) through their codecs. */
class CardMessageCodecTest {

    private static void assertRoundTrip(GameEvent event) {
        assertEquals(event, GameEventCodec.decode(GameEventCodec.encode(event)));
    }

    @Test
    void cardEvents_roundTripWithInstanceIds() {
        assertRoundTrip(new CardDrawnEvent(PieceAlignment.P2, "P2-4", "Wolf Cub", 3));
        assertRoundTrip(new CardPlayedEvent("Wolf", PieceAlignment.P1, 0, 2, "piece-9", "P1-0"));
        assertRoundTrip(new CardPlayedEvent("Fireball", PieceAlignment.P1, -1, -1, null, "P1-1"));
        assertRoundTrip(new CardDiscardedEvent(PieceAlignment.P1, 2, List.of("P1-2", "P1-3")));
    }

    @Test
    void playCommands_roundTripWithInstanceIds() {
        NetworkCommand summon = new NetworkCommand.PlaySummonCard(PieceAlignment.P2, "P2-1", 6, 3);
        NetworkCommand spell = new NetworkCommand.PlaySpellCard(PieceAlignment.P2, "P2-2", null, null);
        assertEquals(summon, NetworkCommandCodec.decode(NetworkCommandCodec.encode(summon)));
        assertEquals(spell, NetworkCommandCodec.decode(NetworkCommandCodec.encode(spell)));
    }

    @Test
    void snapshot_roundTripsHandsAsCardInstances() {
        GameSnapshot snapshot = new GameSnapshot(List.of(),
                List.of(new CardInstance("P1-0", "Wolf")), List.of(new CardInstance("P2-5", "Fireball")),
                2, 3, PieceAlignment.P2);
        assertEquals(snapshot, GameSnapshotCodec.decode(GameSnapshotCodec.encode(snapshot)));
    }
}
