package io.github.elderpath_crusade.bot.eval;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.bot.command.BotCommand;
import io.github.elderpath_crusade.bot.search.Coord;
import io.github.elderpath_crusade.bot.search.ThreatMap;

import java.util.List;
import java.util.function.Supplier;

/**
 * Shared types for the bot's modular intent evaluation system.
 */
public class BotActionContext {

    public enum IntentType {
        ADJ_ATTACK,
        WIN_MOVE, WIN_PATH1, WIN_PATH2,
        ADVANCE,
        MANEUVER,
        DEF_SUMMON
    }

    public record WinPathResult(int turns, Coord firstMove, int threatExposure, boolean endsTurn0InThreat) {
    }

    public record Intent(int score, Supplier<Boolean> execute, IntentType kind, BotCommand command) {
        public Intent(int score, Supplier<Boolean> execute, IntentType kind) {
            this(score, execute, kind, null);
        }
    }

    public record PieceEntry(Coord pos, Entity entity) {
    }

    public record TacticalState(List<PieceEntry> allies, List<PieceEntry> enemies, ThreatMap threats) {
    }
}
