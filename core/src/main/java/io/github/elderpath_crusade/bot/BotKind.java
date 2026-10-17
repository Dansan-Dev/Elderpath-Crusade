package io.github.elderpath_crusade.bot;

import io.github.elderpath_crusade.bot.impl.BasicBot;
import io.github.elderpath_crusade.bot.impl.SmartBot;

/** Which bot implementation plays a bot-controlled slot. */
public enum BotKind {
    BASIC,
    SMART;

    public Bot create() {
        return switch (this) {
            case BASIC -> new BasicBot();
            case SMART -> new SmartBot();
        };
    }
}
