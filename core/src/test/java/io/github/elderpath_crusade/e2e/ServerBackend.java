package io.github.elderpath_crusade.e2e;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.LegalActions;
import io.github.elderpath_crusade.match.MatchConfig;
import io.github.elderpath_crusade.net.protocol.MatchView;
import io.github.elderpath_crusade.net.transport.ServerConnection;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Where an end-to-end match's server runs and how clients reach it. The harness only talks to
 * the server through this, so the same scenarios run against every backend.
 *
 * A backend whose server lives in this JVM can also expose its authoritative state (for
 * replica checks); an out-of-process one returns empty and the harness falls back to
 * comparing the clients' replicas with each other.
 */
public interface ServerBackend extends AutoCloseable {

    enum Kind {
        /** MatchServer in this JVM, clients over InProcessTransport (every message still goes through the codec). */
        IN_PROCESS,
        /** MatchServer in this JVM, clients over localhost TCP (ephemeral port). */
        TCP;
        // The third backend, a ServerLauncher child process (ChildProcessBackend), lives in the server
        // module's tests: it needs that module on the classpath and the real assets.

        public ServerBackend create() {
            return switch (this) {
                case IN_PROCESS -> new InJvmBackend.InProcess();
                case TCP -> new InJvmBackend.Tcp();
            };
        }
    }

    String name();

    /** Starts a server for config. Bot seats come from config.bots(); every other side is a human seat. */
    void start(MatchConfig config) throws IOException;

    /** A new client connection to the server; rawTap sees every line the server sends it. */
    ServerConnection connect(Consumer<String> rawTap) throws IOException;

    /** Advances the server one step of deltaSeconds. A server that ticks itself does nothing here. */
    void tick(float deltaSeconds);

    /** Whether messages arrive asynchronously, so the harness must poll until things are quiet. */
    boolean asynchronous();

    /**
     * How long nothing may arrive before an asynchronous backend counts as quiet. A server on its
     * own clock acts only on its ticks, so this must span several of them — otherwise a step can
     * "settle" between two ticks, before the server has handled a handshake or a Leave.
     */
    default long quietMillis() {
        return 0;
    }

    /** The authoritative state as viewer may see it — empty when the server isn't in this JVM. */
    Optional<MatchView> authoritativeView(PieceAlignment viewer);

    /** What viewer's seat may do, as the server computes it — empty when the server isn't in this JVM. */
    default Optional<LegalActions> authoritativeLegalActions(PieceAlignment viewer) {
        return Optional.empty();
    }

    /** Turns started so far, or empty when not observable from here. */
    Optional<Integer> turnsStarted();

    /** Whether the match has ended, or empty when not observable from here. */
    Optional<Boolean> ended();

    /** The winner once the match has ended, or empty (not ended, or not observable). */
    Optional<PieceAlignment> winner();

    /** Whether a bot seat has a pick pending (bots answer synchronously, so this means a stall). False when unobservable. */
    boolean botPickPending();

    @Override
    void close();
}
