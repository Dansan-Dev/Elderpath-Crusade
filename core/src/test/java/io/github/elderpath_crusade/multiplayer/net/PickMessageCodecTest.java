package io.github.elderpath_crusade.multiplayer.net;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.PickKind;
import io.github.elderpath_crusade.match.PickRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Wire round-trips for the ability/pick commands and the host's pick messages (ARCH-020/021). */
class PickMessageCodecTest {

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

    @Test
    void pickRequested_roundTrips() {
        PickRequest request = new PickRequest(5L, PieceAlignment.P2, PickKind.TILE,
                List.of(new Coord(1, 2), new Coord(3, 4)), "Displace", true);
        assertEquals(request, PickMessageCodec.decodeRequested(PickMessageCodec.encodeRequested(request)));
    }

    @Test
    void pickCancelled_roundTrips() {
        assertEquals(42L, PickMessageCodec.decodeCancelledPickId(PickMessageCodec.encodeCancelled(42L)));
    }
}
