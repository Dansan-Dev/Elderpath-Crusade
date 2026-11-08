package io.github.elderpath_crusade.cli;

import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.client.LocalMatchSpec;
import io.github.elderpath_crusade.net.protocol.SeatRequest;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Session-setup flags for the CLI — parses into a {@link ClientMatchSession} the exact same way
 * {@code DemoRoom}/{@code LocalMatchRoom}/{@code OnlineMatchRoom} do (same {@link LocalMatchSpec}
 * factories, same session factory methods), just read from argv instead of a draft screen.
 */
public final class CliOptions {
    public enum Mode { SOLO, HOTSEAT, HOST, JOIN }

    static final String DEFAULT_DECK = "Wolf:2,Wolf Cub:2";
    private static final int DEFAULT_PORT = 4567;

    private Mode mode = Mode.SOLO;
    private List<String> p1Deck = parseDeck(DEFAULT_DECK);
    private List<String> p2Deck = parseDeck(DEFAULT_DECK);
    private BotKind bot = BotKind.SMART;
    private String bindAddress = "0.0.0.0";
    private int port = DEFAULT_PORT;
    private String host;
    private SeatRequest seat = SeatRequest.ANY;
    private String playerName = "player";
    private File assetsDir;
    private Long seed;
    private String scriptPath;

    private CliOptions() {}

    public static CliOptions parse(String[] args) {
        CliOptions o = new CliOptions();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--mode" -> o.mode = parseMode(next(args, ++i));
                case "--p1-deck" -> o.p1Deck = parseDeck(next(args, ++i));
                case "--p2-deck" -> o.p2Deck = parseDeck(next(args, ++i));
                case "--bot" -> o.bot = parseBot(next(args, ++i));
                case "--bind" -> o.bindAddress = next(args, ++i);
                case "--port" -> o.port = parsePort(next(args, ++i));
                case "--host" -> o.host = next(args, ++i);
                case "--seat" -> o.seat = parseSeat(next(args, ++i));
                case "--name" -> o.playerName = next(args, ++i);
                case "--assets" -> o.assetsDir = new File(next(args, ++i));
                case "--seed" -> o.seed = parseSeed(next(args, ++i));
                case "--script" -> o.scriptPath = next(args, ++i);
                default -> throw new IllegalArgumentException("unknown flag: " + arg);
            }
        }
        if (o.mode == Mode.JOIN && o.host == null) {
            throw new IllegalArgumentException("--mode join requires --host");
        }
        return o;
    }

    public Mode mode() {
        return mode;
    }

    /** The script file to read commands from, or null for interactive stdin — wired up by CLI-004. */
    public String scriptPath() {
        return scriptPath;
    }

    /** Starts the session this configuration describes — launches a local server (solo/hotseat/host) or connects (join). */
    public ClientMatchSession startSession() {
        return switch (mode) {
            case SOLO -> ClientMatchSession.startLocal(soloSpec());
            case HOTSEAT -> ClientMatchSession.startLocal(twoHumansSpec());
            case HOST -> ClientMatchSession.host(twoHumansSpec(), bindAddress, port);
            case JOIN -> ClientMatchSession.join(host, port, seat, playerName);
        };
    }

    private LocalMatchSpec soloSpec() {
        return applyCommon(LocalMatchSpec.solo(p1Deck, p2Deck, bot));
    }

    private LocalMatchSpec twoHumansSpec() {
        return applyCommon(LocalMatchSpec.twoHumans(p1Deck, p2Deck));
    }

    private LocalMatchSpec applyCommon(LocalMatchSpec spec) {
        spec = spec.withPlayerName(playerName).withAssetsDir(assetsDir);
        return seed != null ? spec.withSeed(seed) : spec;
    }

    /** "Wolf:2,Wolf Cub:2" → [Wolf, Wolf, Wolf Cub, Wolf Cub] — LocalMatchSpec's own deck format, reversed. */
    static List<String> parseDeck(String spec) {
        List<String> out = new ArrayList<>();
        for (String part : spec.split(",")) {
            String[] nameCount = part.split(":");
            if (nameCount.length != 2) {
                throw new IllegalArgumentException("bad deck entry (want Name:count): " + part);
            }
            String name = nameCount[0].trim();
            if (name.isEmpty()) throw new IllegalArgumentException("bad deck entry (empty name): " + part);
            int count = parsePositiveInt(nameCount[1].trim(), "deck count");
            for (int i = 0; i < count; i++) out.add(name);
        }
        if (out.isEmpty()) throw new IllegalArgumentException("empty deck");
        return out;
    }

    private static Mode parseMode(String value) {
        try {
            return Mode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("--mode must be solo, hotseat, host or join (was: " + value + ")");
        }
    }

    private static BotKind parseBot(String value) {
        try {
            return BotKind.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("--bot must be basic or smart (was: " + value + ")");
        }
    }

    private static SeatRequest parseSeat(String value) {
        try {
            return SeatRequest.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("--seat must be p1, p2 or any (was: " + value + ")");
        }
    }

    private static int parsePort(String value) {
        return parsePositiveInt(value, "--port");
    }

    private static long parseSeed(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--seed must be a number (was: " + value + ")");
        }
    }

    private static int parsePositiveInt(String value, String what) {
        int n;
        try {
            n = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " must be a number (was: " + value + ")");
        }
        if (n < 0) throw new IllegalArgumentException(what + " must not be negative (was: " + value + ")");
        return n;
    }

    private static String next(String[] args, int i) {
        if (i >= args.length) throw new IllegalArgumentException("missing value after " + args[i - 1]);
        return args[i];
    }
}
