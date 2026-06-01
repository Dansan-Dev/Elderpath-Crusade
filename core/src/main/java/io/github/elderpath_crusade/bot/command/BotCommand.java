package io.github.elderpath_crusade.bot.command;

/**
 * Sealed interface for replayable, serializable bot actions.
 */
public sealed interface BotCommand permits MoveCommand, AttackCommand, SummonCommand, EndTurnCommand {
}
