package io.github.elderpath_crusade.server.launcher;

import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.enums.PieceAlignment;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * ServerLauncher's command line. Every flag is optional:
 * <pre>
 *   --port N            TCP port, 0 = any free port (default 0)
 *   --bind ADDR         address to listen on (default 127.0.0.1; 0.0.0.0 to host for others)
 *   --p1 / --p2 KIND    human | basic | smart (default p1 human, p2 smart)
 *   --p1-deck / --p2-deck "Card:count,Card:count"   (default Wolf:2,Wolf Cub:2)
 *   --seed N            deck shuffle seed (default random)
 *   --assets DIR        assets directory holding data/*.yaml (default: the working directory if it
 *                       holds data/pieces.yaml, else the classpath — a packaged game's jar)
 *   --tick-ms N         server tick interval (default 16)
 *   --time-scale F      game-time multiplier, e.g. to fast-forward bot pacing (default 1)
 *   --parent-pid PID    exit once this process is gone
 *   --watch-stdin B     exit when stdin closes (default true)
 *   --idle-timeout-s N  exit after N s with no human client connected (default 60)
 *   --end-grace-s N     after the match ends, exit within N s (default 3)
 * </pre>
 */
record LaunchOptions(
        int port,
        String bind,
        Map<PieceAlignment, BotKind> bots,
        Map<PieceAlignment, List<String>> decks,
        Long seed,
        File assetsDir,
        int tickMs,
        float timeScale,
        Long parentPid,
        boolean watchStdin,
        int idleTimeoutSeconds,
        int endGraceSeconds
) {
    static final String DEFAULT_DECK = "Wolf:2,Wolf Cub:2";

    /** Parses args, throwing IllegalArgumentException with a usage message on anything invalid. */
    static LaunchOptions parse(String[] args) {
        int port = 0;
        String bind = "127.0.0.1";
        String p1 = "human";
        String p2 = "smart";
        String p1Deck = DEFAULT_DECK;
        String p2Deck = DEFAULT_DECK;
        Long seed = null;
        File assetsDir = null; // null = working directory or classpath, see ServerLauncher
        int tickMs = 16;
        float timeScale = 1f;
        Long parentPid = null;
        boolean watchStdin = true;
        int idleTimeout = 60;
        int endGrace = 3;

        for (int i = 0; i < args.length; i++) {
            String flag = args[i];
            if (i + 1 >= args.length) throw new IllegalArgumentException("missing value for " + flag);
            String value = args[++i];
            switch (flag) {
                case "--port" -> port = parseInt(flag, value, 0, 65535);
                case "--bind" -> bind = value;
                case "--p1" -> p1 = value;
                case "--p2" -> p2 = value;
                case "--p1-deck" -> p1Deck = value;
                case "--p2-deck" -> p2Deck = value;
                case "--seed" -> seed = parseLong(flag, value);
                case "--assets" -> assetsDir = new File(value);
                case "--tick-ms" -> tickMs = parseInt(flag, value, 1, 1000);
                case "--time-scale" -> timeScale = parseScale(value);
                case "--parent-pid" -> parentPid = parseLong(flag, value);
                case "--watch-stdin" -> watchStdin = Boolean.parseBoolean(value);
                case "--idle-timeout-s" -> idleTimeout = parseInt(flag, value, 1, Integer.MAX_VALUE);
                case "--end-grace-s" -> endGrace = parseInt(flag, value, 0, Integer.MAX_VALUE);
                default -> throw new IllegalArgumentException("unknown flag " + flag);
            }
        }

        Map<PieceAlignment, BotKind> bots = new EnumMap<>(PieceAlignment.class);
        putBot(bots, PieceAlignment.P1, p1);
        putBot(bots, PieceAlignment.P2, p2);
        Map<PieceAlignment, List<String>> decks = new EnumMap<>(PieceAlignment.class);
        decks.put(PieceAlignment.P1, parseDeck(p1Deck));
        decks.put(PieceAlignment.P2, parseDeck(p2Deck));
        return new LaunchOptions(port, bind, bots, decks, seed, assetsDir, tickMs, timeScale,
                parentPid, watchStdin, idleTimeout, endGrace);
    }

    /** Sides played by humans (no bot) — the seats clients connect to. */
    List<PieceAlignment> humanSides() {
        List<PieceAlignment> out = new ArrayList<>();
        for (PieceAlignment side : new PieceAlignment[]{PieceAlignment.P1, PieceAlignment.P2}) {
            if (!bots.containsKey(side)) out.add(side);
        }
        return out;
    }

    private static void putBot(Map<PieceAlignment, BotKind> bots, PieceAlignment side, String kind) {
        switch (kind) {
            case "human" -> { }
            case "basic" -> bots.put(side, BotKind.BASIC);
            case "smart" -> bots.put(side, BotKind.SMART);
            default -> throw new IllegalArgumentException("--" + side.name().toLowerCase()
                    + " must be human, basic or smart, not " + kind);
        }
    }

    /** "Wolf:2,Wolf Cub:2" → [Wolf, Wolf, Wolf Cub, Wolf Cub]; a count may be left out (= 1). */
    static List<String> parseDeck(String spec) {
        List<String> deck = new ArrayList<>();
        for (String part : spec.split(",")) {
            String entry = part.trim();
            if (entry.isEmpty()) continue;
            int colon = entry.lastIndexOf(':');
            String name = colon < 0 ? entry : entry.substring(0, colon).trim();
            int count = colon < 0 ? 1 : parseInt("deck count for " + name, entry.substring(colon + 1).trim(), 1, 100);
            deck.addAll(Collections.nCopies(count, name));
        }
        if (deck.isEmpty()) throw new IllegalArgumentException("empty deck: " + spec);
        return deck;
    }

    private static int parseInt(String what, String value, int min, int max) {
        try {
            int n = Integer.parseInt(value);
            if (n < min || n > max) throw new IllegalArgumentException(what + " out of range: " + value);
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " is not a number: " + value);
        }
    }

    private static long parseLong(String what, String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " is not a number: " + value);
        }
    }

    private static float parseScale(String value) {
        try {
            float f = Float.parseFloat(value);
            if (!(f > 0)) throw new IllegalArgumentException("--time-scale must be positive: " + value);
            return f;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--time-scale is not a number: " + value);
        }
    }
}
