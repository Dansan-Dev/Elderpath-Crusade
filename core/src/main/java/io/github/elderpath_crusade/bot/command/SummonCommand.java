package io.github.elderpath_crusade.bot.command;

public record SummonCommand(String cardName, int row, int col) implements BotCommand {
}
