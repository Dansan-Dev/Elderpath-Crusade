package io.github.elderpath_crusade.managers;

import com.badlogic.gdx.utils.Timer;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.bot.Bot;
import io.github.elderpath_crusade.bot.impl.BasicBot;
import io.github.elderpath_crusade.bot.impl.SmartBot;
import io.github.elderpath_crusade.events.TurnStartedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.utils.Logger;

/**
 * Delegates P2 TURN_STARTED to a configured Bot implementation.
 * The chosen bot drives its own pacing and ends the turn when finished.
 */
public final class BotManager {
    private boolean initialized = false;
    private static final float DELAY_BEFORE_ACT = 0.4f;
    private Bot bot = null;

    public BotManager() {}

    public void initialize() {
        if (initialized) return;
        initialized = true;
        if (bot == null) {
            try {
                bot = new SmartBot();
            } catch (Throwable t) {
                Logger.error("BotManager", "Failed to init SmartBot, falling back to BasicBot: " + t.getMessage());
                bot = new BasicBot();
            }
        }

        TypedEventBus.get().register(TurnStartedEvent.class, evt -> {
            if (GameContext.get().getGameModeManager().getCurrent() == GameMode.LOCAL_MATCH) return;
            if (!SettingsManager.debug.enableP2Bot) return;
            if (evt.player() != PieceAlignment.P2) return;
            if (GameContext.get().getGameManager().isPaused()) return;
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
        });
    }

    public void setBot(Bot bot) { this.bot = bot; }
}
