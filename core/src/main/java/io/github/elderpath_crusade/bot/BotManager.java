package io.github.elderpath_crusade.bot;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TurnStartedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.utils.Logger;

import java.util.EnumMap;
import java.util.Map;

/**
 * Plays the match's bot-controlled slots: on TURN_STARTED for a slot that has a bot, hands
 * the turn to that slot's Bot (one instance per slot). The bot drives its own pacing via
 * the MatchClock and ends the turn when finished. Slots come from the MatchConfig (see
 * MatchSetup) — any alignment can be a bot, including both (bot vs bot).
 */
public final class BotManager {
    private static final float DELAY_BEFORE_ACT = 0.4f;

    private boolean initialized = false;
    private final Map<PieceAlignment, Bot> slots = new EnumMap<>(PieceAlignment.class);

    public BotManager() {}

    public void initialize() {
        if (initialized) return;
        initialized = true;

        TypedEventBus.get().registerScoped("session", TurnStartedEvent.class, evt -> {
            Bot bot = slots.get(evt.player());
            if (bot == null) return;
            if (GameContext.get().getGameManager().isPaused()) return;
            PieceAlignment player = evt.player();
            GameContext.get().getMatchClock().schedule(DELAY_BEFORE_ACT, () -> {
                try {
                    Logger.log("BotManager", "Starting " + player + " turn with bot: " + bot.getName());
                    bot.onTurnStarted(player);
                } catch (Exception ex) {
                    Logger.error("BotManager", "Exception in bot.onTurnStarted: " + ex.getMessage());
                }
            });
        });
    }

    /** Starts a new match session with these bot-controlled slots (fresh bot instances). */
    public void resetSession(Map<PieceAlignment, BotKind> bots) {
        slots.clear();
        bots.forEach((alignment, kind) -> slots.put(alignment, kind.create()));
        initialized = false;
        initialize();
    }

    /** Replaces the bot playing a slot (null makes it human-controlled). */
    public void setBot(PieceAlignment alignment, Bot bot) {
        if (bot == null) slots.remove(alignment);
        else slots.put(alignment, bot);
    }

    /** True if the given alignment is currently played by a bot rather than a human. */
    public static boolean isBotControlled(PieceAlignment alignment) {
        return alignment != null && GameContext.get().getBotManager().slots.containsKey(alignment);
    }
}
