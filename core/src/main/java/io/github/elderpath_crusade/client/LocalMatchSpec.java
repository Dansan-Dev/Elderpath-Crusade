package io.github.elderpath_crusade.client;

import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.enums.PieceAlignment;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a match server this client launches should run: each side's deck, which sides are bots
 * (the rest are human seats), an optional shuffle seed, the player name, and where the server
 * reads its assets (null = resolve it — see ClientMatchSession). serverArgs are extra
 * ServerLauncher flags (e.g. --time-scale in tests).
 */
public record LocalMatchSpec(
        Map<PieceAlignment, List<String>> decks,
        Map<PieceAlignment, BotKind> bots,
        Long seed,
        String playerName,
        File assetsDir,
        List<String> serverArgs
) {
    private static final PieceAlignment[] SIDES = {PieceAlignment.P1, PieceAlignment.P2};

    public LocalMatchSpec {
        for (PieceAlignment side : SIDES) {
            List<String> deck = decks.get(side);
            if (deck == null || deck.isEmpty()) throw new IllegalArgumentException("no deck for " + side);
        }
        decks = Map.copyOf(decks);
        bots = Map.copyOf(bots);
        serverArgs = List.copyOf(serverArgs);
        if (playerName == null || playerName.isBlank()) playerName = "player";
    }

    /** A human (P1) against a bot (P2). */
    public static LocalMatchSpec solo(List<String> humanDeck, List<String> botDeck, BotKind bot) {
        return new LocalMatchSpec(Map.of(PieceAlignment.P1, humanDeck, PieceAlignment.P2, botDeck),
                Map.of(PieceAlignment.P2, bot), null, "player", null, List.of());
    }

    /** Two humans — hotseat on one machine, or a host and a remote guest. */
    public static LocalMatchSpec twoHumans(List<String> p1Deck, List<String> p2Deck) {
        return new LocalMatchSpec(Map.of(PieceAlignment.P1, p1Deck, PieceAlignment.P2, p2Deck),
                Map.of(), null, "player", null, List.of());
    }

    public LocalMatchSpec withSeed(Long value) {
        return new LocalMatchSpec(decks, bots, value, playerName, assetsDir, serverArgs);
    }

    public LocalMatchSpec withPlayerName(String value) {
        return new LocalMatchSpec(decks, bots, seed, value, assetsDir, serverArgs);
    }

    public LocalMatchSpec withAssetsDir(File value) {
        return new LocalMatchSpec(decks, bots, seed, playerName, value, serverArgs);
    }

    public LocalMatchSpec withServerArgs(String... args) {
        return new LocalMatchSpec(decks, bots, seed, playerName, assetsDir, List.of(args));
    }

    /** Sides with no bot — the human seats. */
    public List<PieceAlignment> humanSides() {
        List<PieceAlignment> out = new ArrayList<>();
        for (PieceAlignment side : SIDES) if (!bots.containsKey(side)) out.add(side);
        return out;
    }

    /** The ServerLauncher flags for this match (bind/port/assets/parent are added by the session). */
    List<String> launcherArgs() {
        List<String> args = new ArrayList<>();
        for (PieceAlignment side : SIDES) {
            String flag = "--" + side.name().toLowerCase();
            BotKind bot = bots.get(side);
            args.add(flag);
            args.add(bot == null ? "human" : bot.name().toLowerCase());
            args.add(flag + "-deck");
            args.add(deckSpec(decks.get(side)));
        }
        if (seed != null) {
            args.add("--seed");
            args.add(seed.toString());
        }
        args.addAll(serverArgs);
        return args;
    }

    /** [Wolf, Wolf, Wolf Cub] → "Wolf:2,Wolf Cub:1" (ServerLauncher's --pN-deck format). */
    static String deckSpec(List<String> deck) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String card : deck) {
            if (card.contains(",") || card.contains(":")) {
                throw new IllegalArgumentException("card name can't be passed to the server: " + card);
            }
            counts.merge(card, 1, Integer::sum);
        }
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (out.length() > 0) out.append(',');
            out.append(e.getKey()).append(':').append(e.getValue());
        }
        return out.toString();
    }
}
