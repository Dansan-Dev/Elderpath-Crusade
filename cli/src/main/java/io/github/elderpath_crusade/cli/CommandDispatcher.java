package io.github.elderpath_crusade.cli;

import io.github.elderpath_crusade.client.ClientCommands;

/**
 * Sends a parsed {@link CliCommand} through a session's {@link ClientCommands} — a thin call to
 * whichever method matches, exactly like {@code ClientController} does for the GUI. Does no
 * legality checking of its own; the server re-validates everything. {@link CliCommand.Show},
 * {@link CliCommand.Legal}, {@link CliCommand.Board} and {@link CliCommand.Quit} aren't server
 * commands — CLI-004's loop handles those directly, so {@link #dispatch} is a no-op for them.
 */
public final class CommandDispatcher {
    private CommandDispatcher() {}

    /** Dispatches an action command to commands; returns the command's seq, or NOT_SENT for a display/control command. */
    public static long dispatch(CliCommand command, ClientCommands commands) {
        // Java 17 (this project's target) has no pattern-matching switch over a sealed interface's
        // record subtypes (that needs 21+) — an instanceof chain is the equivalent exhaustive dispatch.
        if (command instanceof CliCommand.Move m) {
            return commands.moveOrAttack(m.src().row(), m.src().col(), m.dst().row(), m.dst().col());
        }
        if (command instanceof CliCommand.Summon s) {
            return commands.playSummon(s.instanceId(), s.at().row(), s.at().col());
        }
        if (command instanceof CliCommand.Spell s) {
            return commands.playSpell(s.instanceId(), targetRow(s), targetCol(s));
        }
        if (command instanceof CliCommand.Ability a) {
            return commands.activateAbility(a.pieceId(), a.actionIndex());
        }
        if (command instanceof CliCommand.Pick p) {
            return commands.answerPick(p.choice());
        }
        if (command instanceof CliCommand.CancelPick) {
            return commands.cancelPick();
        }
        if (command instanceof CliCommand.EndTurn) {
            return commands.endTurn();
        }
        // Show / Legal / Board / Quit: not server commands — CLI-004's loop handles these directly.
        return ClientCommands.NOT_SENT;
    }

    private static Integer targetRow(CliCommand.Spell s) {
        return s.at() == null ? null : s.at().row();
    }

    private static Integer targetCol(CliCommand.Spell s) {
        return s.at() == null ? null : s.at().col();
    }
}
