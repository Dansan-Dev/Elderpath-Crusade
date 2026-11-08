package io.github.elderpath_crusade.run;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RunDifficultyTest {

    @Test
    void default_isMinLevel() {
        assertEquals(RunDifficulty.MIN_LEVEL, RunDifficulty.DEFAULT.level());
    }

    @Test
    void accepts_everyLevelInRange() {
        for (int level = RunDifficulty.MIN_LEVEL; level <= RunDifficulty.MAX_LEVEL; level++) {
            assertEquals(level, new RunDifficulty(level).level());
        }
    }

    @Test
    void rejects_belowMin() {
        assertThrows(IllegalArgumentException.class, () -> new RunDifficulty(RunDifficulty.MIN_LEVEL - 1));
    }

    @Test
    void rejects_aboveMax() {
        assertThrows(IllegalArgumentException.class, () -> new RunDifficulty(RunDifficulty.MAX_LEVEL + 1));
    }
}
