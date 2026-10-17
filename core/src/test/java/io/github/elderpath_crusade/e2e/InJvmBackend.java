package io.github.elderpath_crusade.e2e;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TurnStartedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.match.MatchConfig;
import io.github.elderpath_crusade.net.protocol.MatchView;
import io.github.elderpath_crusade.net.transport.InProcessTransport;
import io.github.elderpath_crusade.net.transport.ServerConnection;
import io.github.elderpath_crusade.net.transport.ServerTransport;
import io.github.elderpath_crusade.net.transport.TcpTransport;
import io.github.elderpath_crusade.server.MatchServer;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;

/** A MatchServer in this JVM (one match per headless context), reached over some transport. */
abstract class InJvmBackend implements ServerBackend {
    private MatchServer server;
    private MatchConfig config;
    private int turnsStarted;

    abstract ServerTransport openTransport() throws IOException;

    @Override
    public void start(MatchConfig config) throws IOException {
        this.config = config;
        // Not session-scoped: MatchServer's MatchSetup.prepare clears the "session" group.
        TypedEventBus.get().register(TurnStartedEvent.class, e -> turnsStarted++);
        server = new MatchServer(config, openTransport());
    }

    @Override
    public void tick(float deltaSeconds) {
        server.tick(deltaSeconds);
    }

    @Override
    public Optional<MatchView> authoritativeView(PieceAlignment viewer) {
        return Optional.of(server.viewFor(viewer));
    }

    @Override
    public Optional<Integer> turnsStarted() {
        return Optional.of(turnsStarted);
    }

    @Override
    public Optional<Boolean> ended() {
        return Optional.of(server.isEnded());
    }

    @Override
    public Optional<PieceAlignment> winner() {
        return server.isEnded() ? Optional.ofNullable(server.getWinner()) : Optional.empty();
    }

    @Override
    public boolean botPickPending() {
        for (PieceAlignment side : config.bots().keySet()) {
            if (GameContext.get().getPickRouter().hasPending(side)) return true;
        }
        return false;
    }

    @Override
    public void close() {
        if (server != null) server.close();
    }

    static final class InProcess extends InJvmBackend {
        private InProcessTransport transport;

        @Override
        ServerTransport openTransport() {
            transport = new InProcessTransport();
            return transport;
        }

        @Override
        public String name() {
            return "IN_PROCESS";
        }

        @Override
        public ServerConnection connect(Consumer<String> rawTap) {
            return transport.connect(rawTap);
        }

        @Override
        public boolean asynchronous() {
            return false;
        }
    }

    static final class Tcp extends InJvmBackend {
        private TcpTransport transport;

        @Override
        ServerTransport openTransport() throws IOException {
            transport = new TcpTransport("127.0.0.1", 0);
            return transport;
        }

        @Override
        public String name() {
            return "TCP";
        }

        @Override
        public ServerConnection connect(Consumer<String> rawTap) throws IOException {
            return TcpTransport.connect("127.0.0.1", transport.getPort(), rawTap);
        }

        @Override
        public boolean asynchronous() {
            return true;
        }
    }
}
