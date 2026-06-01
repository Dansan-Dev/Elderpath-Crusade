package io.github.elderpath_crusade.bot.command;

public record AttackCommand(String pieceId, int fromRow, int fromCol, int targetRow, int targetCol) implements BotCommand {
}
