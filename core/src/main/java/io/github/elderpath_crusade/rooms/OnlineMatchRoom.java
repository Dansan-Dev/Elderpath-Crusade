package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.client.LocalMatchSpec;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.net.protocol.SeatRequest;
import io.github.elderpath_crusade.supers.Room;

import java.util.ArrayList;
import java.util.List;

/**
 * A tactical battle over the network, run entirely by a match server: hosting launches a child
 * server bound publicly and connects as whichever seat is free (P1 for the first connection),
 * joining connects to a host's address. Both paths now go through the exact same BattleRoom
 * machinery as solo/hotseat (hand sync, picks, clicks, highlights) — no more host/guest-specific
 * wiring (the old GameHost/GameClient/ActionDispatcher/ReplicaEventApplier prototype).
 *
 * Skips the draft flow for now — real cross-network deck sharing needs a lobby to carry a
 * guest's draft to the host before the server even launches, which is phase 4 (FEAT-008)
 * territory. Both sides start with the same fixed deck (2x Wolf + 2x Wolf Cub, matching
 * DraftRoom's starting deck and the pre-ARCH-028 prototype's behavior) — not a regression.
 */
public class OnlineMatchRoom extends BattleRoom {
    private static final int STARTING_WOLVES = 2;
    private static final int STARTING_WOLF_CUBS = 2;
    private static final int DEFAULT_PORT = 4567;

    private OnlineMatchRoom(ClientMatchSession session) {
        super(GameMode.ONLINE_MATCH, session);
        layoutBoard();
    }

    @Override
    protected void onPassTurnClicked() {
        controller.endTurn();
    }

    public static Room host() {
        return MatchStartingRoom.get(OnlineMatchRoom::startHosting, OnlineMatchRoom::new);
    }

    public static Room join(String address) {
        return MatchStartingRoom.get(() -> startJoining(address), OnlineMatchRoom::new);
    }

    private static ClientMatchSession startHosting() {
        LocalMatchSpec spec = LocalMatchSpec.twoHumans(startingDeck(), startingDeck());
        return ClientMatchSession.host(spec, "0.0.0.0", DEFAULT_PORT);
    }

    private static ClientMatchSession startJoining(String address) {
        return ClientMatchSession.join(address, DEFAULT_PORT, SeatRequest.ANY, "player");
    }

    private static List<String> startingDeck() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < STARTING_WOLVES; i++) names.add("Wolf");
        for (int i = 0; i < STARTING_WOLF_CUBS; i++) names.add("Wolf Cub");
        return names;
    }
}
