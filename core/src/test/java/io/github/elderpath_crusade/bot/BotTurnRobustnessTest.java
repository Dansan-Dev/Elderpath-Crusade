package io.github.elderpath_crusade.bot;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.abilities.data.AbilityDefinition;
import io.github.elderpath_crusade.abilities.data.ModifierDef;
import io.github.elderpath_crusade.abilities.data.TargetSelector;
import io.github.elderpath_crusade.data.AbilityRef;
import io.github.elderpath_crusade.data.AbilityRegistry;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.GameWonEvent;
import io.github.elderpath_crusade.events.TurnEndedEvent;
import io.github.elderpath_crusade.events.TurnStartedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.match.CardInstance;
import io.github.elderpath_crusade.match.MatchConfig;
import io.github.elderpath_crusade.match.MatchSetup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * BUG-027 regression: every bot turn ends, however the match clock is ticked — including the
 * huge, irregular deltas a loaded server produces (the launcher ticks with wall-clock delta ×
 * time scale, so one tick can cover seconds of match time). Content mirrors the real starting
 * decks (Wolf with its PackHunter aura, Wolf Cub), defined in code.
 */
class BotTurnRobustnessTest {
    private static final PieceDefinition WOLF = new PieceDefinition("Wolf", 1, 1, 1, 1, 1, List.of(new AbilityRef("PackHunter", Map.of())));
    private static final PieceDefinition WOLF_CUB = new PieceDefinition("WolfCub", 0, 1, 0, 1, 1, List.of());
    private static final AbilityDefinition PACK_HUNTER = new AbilityDefinition("PackHunter", "PackHunter", Map.of(), List.of(), List.of(),
            List.of(new ModifierDef(new TargetSelector("AdjacentFriendlyUnits", Map.of("unitName", "WolfCub")), Map.of("addDamage", 1))));

    @AfterEach
    void tearDown() {
        CardCatalog.clear();
        TypedEventBus.get().clear();
    }

    @Test
    void humanVsSmartBot_everyBotTurnEnds_underIrregularLargeTicks() {
        for (long seed = 1; seed <= 40; seed++) {
            play(seed, Map.of(PieceAlignment.P2, BotKind.SMART), "human P1 vs SMART P2");
        }
    }

    @Test
    void botVsBot_everyBotTurnEnds_underIrregularLargeTicks() {
        for (BotKind kind : BotKind.values()) {
            for (long seed = 1; seed <= 20; seed++) {
                play(seed, Map.of(PieceAlignment.P1, kind, PieceAlignment.P2, kind), kind + " vs " + kind);
            }
        }
    }

    @Test
    void pausingDuringABotTurn_delaysIt_butTheTurnStillEnds() {
        for (long seed = 1; seed <= 20; seed++) {
            pauseChance = 0.15;
            try {
                play(seed, Map.of(PieceAlignment.P1, BotKind.SMART, PieceAlignment.P2, BotKind.BASIC), "paused SMART vs BASIC");
            } finally {
                pauseChance = 0;
            }
        }
    }

    /** Per tick, the chance to toggle the game's pause (0 = never). */
    private static double pauseChance;

    /** Plays up to 30 turns; a scripted human (if any) summons what it can and passes. */
    private static void play(long seed, Map<PieceAlignment, BotKind> bots, String label) {
        TypedEventBus.get().clear();
        GameContext.createHeadless();
        PieceRegistry.register(WOLF);
        PieceRegistry.register(WOLF_CUB);
        AbilityRegistry.register(PACK_HUNTER);
        CardCatalog.clear();
        CardCatalog.registerSummon("Wolf", "Wolf");
        CardCatalog.registerSummon("Wolf Cub", "WolfCub");

        List<PieceAlignment> started = new ArrayList<>();
        List<PieceAlignment> ended = new ArrayList<>();
        boolean[] won = new boolean[1];
        TypedEventBus.get().register(TurnStartedEvent.class, e -> started.add(e.player()));
        TypedEventBus.get().register(TurnEndedEvent.class, e -> ended.add(e.player()));
        TypedEventBus.get().register(GameWonEvent.class, e -> won[0] = true);

        List<String> deck = List.of("Wolf", "Wolf", "Wolf Cub", "Wolf Cub");
        MatchSetup.start(MatchConfig.standard(GameMode.DEMO, new ArrayList<>(deck), new ArrayList<>(deck), seed, bots));

        Random deltas = new Random(seed * 31);
        int ticksThisTurn = 0;
        int turnsSeen = 0;
        for (int tick = 0; tick < 200_000 && !won[0] && started.size() < 30; tick++) {
            PieceAlignment current = GameContext.get().getTurnManager().getCurrentPlayer();
            if (!bots.containsKey(current)) humanTurn(current);
            // Mostly normal frames, sometimes a stall of up to several seconds of match time.
            float delta = deltas.nextInt(10) == 0 ? 0.5f + deltas.nextFloat() * 6f : 0.005f + deltas.nextFloat() * 0.1f;
            if (deltas.nextDouble() < pauseChance) togglePause();
            MatchSetup.tick(delta);
            if (started.size() != turnsSeen) {
                turnsSeen = started.size();
                ticksThisTurn = 0;
            } else if (++ticksThisTurn > 5_000) {
                fail(label + " seed " + seed + ": " + current + "'s turn " + turnsSeen + " never ended");
            }
        }
    }

    /**
     * Flips the pause flag the bots consult. Directly, because GameManager.unpause() also resets
     * the pause screen UI, which loads fonts and so can't run headless.
     */
    private static void togglePause() {
        var game = GameContext.get().getGameManager();
        try {
            Field paused = game.getClass().getDeclaredField("isPaused");
            paused.setAccessible(true);
            paused.setBoolean(game, !game.isPaused());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void humanTurn(PieceAlignment side) {
        var player = GameContext.get().getPlayerManager().get(side);
        var grid = GameContext.get().getActiveGrid();
        for (CardInstance card : List.copyOf(player.hand.getCards())) {
            for (int col = 0; col < grid.getCols(); col++) {
                if (GameContext.get().getGameServer().playSummonCard(side, card.instanceId(), grid.getHomeRow(side), col).isOk()) break;
            }
        }
        GameContext.get().getGameServer().endTurn(side);
    }
}
