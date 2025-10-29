package io.github.elderpath_crusade.managers;

import com.badlogic.gdx.utils.Timer;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.managers.bot.Bot;
import io.github.elderpath_crusade.managers.bot.impl.BasicBot;
import io.github.elderpath_crusade.managers.bot.impl.SmartBot;
import io.github.elderpath_crusade.multiplayer.EventBus;
import io.github.elderpath_crusade.multiplayer.GameEvent;
import io.github.elderpath_crusade.multiplayer.GameEventType;
import io.github.elderpath_crusade.utils.Logger;

import java.util.function.Consumer;

/**
 * Delegates P2 TURN_STARTED to a configured Bot implementation.
 * The chosen bot drives its own pacing and ends the turn when finished.
 */
public final class BotManager {
    private static boolean initialized = false;
    private static final float DELAY_BEFORE_ACT = 0.4f;
    private static Bot bot = null;

    private BotManager() {}

    public static void setBot(Bot customBot) {
        bot = customBot;
    }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        // Default to SmartBot, fallback to BasicBot if needed
        if (bot == null) {
            try {
                bot = new SmartBot();
            } catch (Throwable t) {
                Logger.error("BotManager", "Failed to init SmartBot, falling back to BasicBot: " + t.getMessage());
                bot = new BasicBot();
            }
        }

        Consumer<GameEvent> onTurn = (evt) -> {
            if (evt.getType() != GameEventType.TURN_STARTED) return;
            if (!SettingsManager.debug.enableP2Bot) return;
            Object p = evt.getData().get("player");
            if (p == null) return;
            if (!PieceAlignment.P2.name().equals(p.toString())) return;
            if (GraphicsManager.isPaused()) return;
            if (InteractionManager.hasActiveSelection()) {
                InteractionManager.cancelSelection();
            }
            Timer.schedule(new Timer.Task() {
                @Override
                public void run() {
                    try {
                        if (bot == null) {
                            Logger.error("BotManager", "No bot configured; cannot act");
                            return;
                        }
                        Logger.log("BotManager", "Starting P2 turn with bot: " + bot.getName());
                        bot.onTurnStarted(PieceAlignment.P2);
                    } catch (Exception ex) {
                        Logger.error("BotManager", "Exception in bot.onTurnStarted: " + ex.getMessage());
                    }
                }
            }, DELAY_BEFORE_ACT);
        };
        EventBus.register(GameEventType.TURN_STARTED, onTurn);
    }
}
