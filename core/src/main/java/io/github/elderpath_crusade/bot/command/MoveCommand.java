package io.github.elderpath_crusade.bot.command;

public record MoveCommand(String pieceId, int fromRow, int fromCol, int toRow, int toCol) implements BotCommand {
}
