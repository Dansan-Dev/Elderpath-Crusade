package io.github.elderpath_crusade.bot.eval;

import io.github.elderpath_crusade.game_objects.board.game_piece.MonsterGamePiece;
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

    public record Intent(int score, Supplier<Boolean> execute, IntentType kind) {
    }

    public record PieceEntry(Coord pos, MonsterGamePiece piece) {
    }

    public record TacticalState(List<PieceEntry> allies, List<PieceEntry> enemies, ThreatMap threats) {
    }
}
