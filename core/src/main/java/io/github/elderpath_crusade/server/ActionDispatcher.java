package io.github.elderpath_crusade.server;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.multiplayer.net.NetworkCommand;
import io.github.elderpath_crusade.multiplayer.net.OnlineMatchSession;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Routes an action to local execution (in-process, via GameServer) or network transmission
 * (send a NetworkCommand, when this process is the online guest). Local click handlers call
 * this instead of executing directly, so the same click code works unmodified whether
 * playing locally, hosting, or as the online guest.
 */
public final class ActionDispatcher {
    private ActionDispatcher() {}

    public static boolean dispatch(Supplier<NetworkCommand> toCommand, BooleanSupplier localExecute) {
        OnlineMatchSession session = GameContext.get().getOnlineMatch();
        if (session.isGuest()) {
            session.getClient().send(toCommand.get());
            return true; // optimistic — the authoritative result arrives via relayed events
        }
        return localExecute.getAsBoolean();
    }
}
