package io.github.elderpath_crusade.run;

/**
 * The run's chosen difficulty level, fixed for the whole run (player-selected at run start, via
 * the difficulty-select room — DIFF-001). Depth-based scaling on top of this level, as the run
 * progresses, is DIFF-001's formula; this record is only the chosen baseline.
 */
public record RunDifficulty(int level) {
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 5;
    public static final RunDifficulty DEFAULT = new RunDifficulty(MIN_LEVEL);

    public RunDifficulty {
        if (level < MIN_LEVEL || level > MAX_LEVEL) {
            throw new IllegalArgumentException("level must be between " + MIN_LEVEL + " and " + MAX_LEVEL);
        }
    }
}
