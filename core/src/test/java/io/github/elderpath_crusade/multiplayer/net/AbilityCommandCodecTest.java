package io.github.elderpath_crusade.multiplayer.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Wire round-trips for the ability/pick NetworkCommands (ARCH-020/021). */
class AbilityCommandCodecTest {

    @Test
    void activateAbility_roundTrips() {
        NetworkCommand cmd = new NetworkCommand.ActivateAbility("piece-1", 2);
        assertEquals(cmd, NetworkCommandCodec.decode(NetworkCommandCodec.encode(cmd)));
    }

    @Test
    void answerPick_roundTrips_choiceAndCancel() {
        NetworkCommand chose = new NetworkCommand.AnswerPick(7L, false, 3, 4);
        NetworkCommand cancelled = new NetworkCommand.AnswerPick(8L, true, 0, 0);
        assertEquals(chose, NetworkCommandCodec.decode(NetworkCommandCodec.encode(chose)));
        assertEquals(cancelled, NetworkCommandCodec.decode(NetworkCommandCodec.encode(cancelled)));
    }
}
