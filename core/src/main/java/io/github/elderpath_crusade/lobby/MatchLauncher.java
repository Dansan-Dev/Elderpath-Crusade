package io.github.elderpath_crusade.lobby;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * How the lobby service gets a match server for a started lobby. Production launches a child
 * ServerLauncher process per match; tests supply fakes. launch() must not block the caller (the
 * lobby service ticks on one thread): starting a JVM takes seconds.
 */
public interface MatchLauncher {

    /** Everything that decides a match: both decks (P1 = host) and the seed. Both seats are human. */
    record MatchSpec(List<String> p1Deck, List<String> p2Deck, long seed) {}

    /** A running match server the lobby can open connections to. */
    interface MatchEndpoint extends AutoCloseable {
        /** A new connection to the match server, speaking the match protocol (the player's Hello is forwarded through it). */
        io.github.elderpath_crusade.net.transport.LineLink connect() throws java.io.IOException;

        /** Whether the match server is still running — false once it exited or crashed, so a held match can be released. */
        default boolean isAlive() {
            return true;
        }

        /** Stops the match server (idempotent). */
        @Override
        void close();
    }

    CompletableFuture<MatchEndpoint> launch(MatchSpec spec);
}
