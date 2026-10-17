package io.github.elderpath_crusade.server.launcher;

import com.badlogic.gdx.Gdx;
import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.data.AbilityRegistry;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.e2e.ServerBackend;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.match.MatchConfig;
import io.github.elderpath_crusade.net.protocol.MatchView;
import io.github.elderpath_crusade.net.transport.ServerConnection;
import io.github.elderpath_crusade.net.transport.TcpTransport;
import io.github.elderpath_crusade.server.ServerProcess;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The production path: a real ServerLauncher child JVM (ServerProcess), clients over TCP. The
 * server runs on its own clock and its state isn't visible from here, so the harness checks
 * the clients' replicas against each other instead of against the server.
 *
 * A child process reads the real assets/data — a MatchConfig's decks must use real card names,
 * and its board/terrain are the launcher's standard ones. The test JVM loads the same
 * definitions so client-side drivers know the cards (as a game client would).
 */
final class ChildProcessBackend implements ServerBackend {
    static final String ASSETS = System.getProperty("elderpath.assetsDir", "../assets");

    private ServerProcess process;

    /** Loads the real pieces, abilities, spells and card catalog into this JVM. */
    static void loadRealDefinitions() {
        Gdx.files = new AssetFiles(new File(ASSETS).getAbsoluteFile());
        PieceRegistry.load();
        AbilityRegistry.load();
        CardCatalog.initialize();
    }

    @Override
    public String name() {
        return "CHILD_PROCESS";
    }

    @Override
    public void start(MatchConfig config) throws IOException {
        List<String> args = new ArrayList<>(List.of(
                "--assets", ASSETS,
                "--p1", seatKind(config, PieceAlignment.P1),
                "--p2", seatKind(config, PieceAlignment.P2),
                "--p1-deck", deckSpec(config.deck(PieceAlignment.P1)),
                "--p2-deck", deckSpec(config.deck(PieceAlignment.P2)),
                "--time-scale", "20", "--tick-ms", "5"));
        if (config.seed() != null) args.addAll(List.of("--seed", String.valueOf(config.seed())));
        process = ServerProcess.start(ServerProcess.Options.of(args.toArray(String[]::new)));
    }

    private static String seatKind(MatchConfig config, PieceAlignment side) {
        BotKind bot = config.bots().get(side);
        return bot == null ? "human" : bot.name().toLowerCase();
    }

    /** ["Wolf", "Wolf", "Wolf Cub"] → "Wolf:2,Wolf Cub:1" (the launcher's --pN-deck format). */
    private static String deckSpec(List<String> deck) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String card : deck) counts.merge(card, 1, Integer::sum);
        List<String> parts = new ArrayList<>();
        counts.forEach((card, n) -> parts.add(card + ":" + n));
        return String.join(",", parts);
    }

    @Override
    public ServerConnection connect(Consumer<String> rawTap) throws IOException {
        return TcpTransport.connect("127.0.0.1", process.getPort(), rawTap);
    }

    @Override
    public void tick(float deltaSeconds) {
        // The child ticks itself in real time.
    }

    @Override
    public boolean asynchronous() {
        return true;
    }

    /** Ten of the child's 5 ms ticks. */
    @Override
    public long quietMillis() {
        return 50;
    }

    @Override
    public Optional<MatchView> authoritativeView(PieceAlignment viewer) {
        return Optional.empty();
    }

    @Override
    public Optional<Integer> turnsStarted() {
        return Optional.empty();
    }

    @Override
    public Optional<Boolean> ended() {
        return Optional.of(process.getWinner() != null);
    }

    @Override
    public Optional<PieceAlignment> winner() {
        String winner = process.getWinner();
        if ("P1".equals(winner)) return Optional.of(PieceAlignment.P1);
        if ("P2".equals(winner)) return Optional.of(PieceAlignment.P2);
        return Optional.empty();
    }

    @Override
    public boolean botPickPending() {
        return false; // not observable from outside the process
    }

    @Override
    public void close() {
        if (process != null) process.stop();
    }
}
