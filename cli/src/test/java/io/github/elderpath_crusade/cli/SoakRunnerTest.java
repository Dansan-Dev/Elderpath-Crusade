package io.github.elderpath_crusade.cli;

import io.github.elderpath_crusade.client.AutoPlayer;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.test.RequiresAssets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** CLI-006: the soak runner's reporting, and a short real soak against child-process servers. */
class SoakRunnerTest {
    private static final String ASSETS = System.getProperty("elderpath.assetsDir", "../assets");

    @Test
    void describe_namesTheOutcome_andAFailureCarriesItsReplayFlags() {
        assertEquals("game 2 (seed 41): P1 won — 30 turns, 96 commands, 4.5 s", SoakRunner.describe(
                new SoakRunner.GameResult(2, 41, SoakRunner.Outcome.WON, PieceAlignment.P1, 30, 96, 0, null, 4.5)));
        assertEquals("game 1 (seed 7): turn limit reached — 200 turns, 3000 commands, 20.0 s", SoakRunner.describe(
                new SoakRunner.GameResult(1, 7, SoakRunner.Outcome.TURN_LIMIT, null, 200, 3000, 0, null, 20.0)));
        String failed = SoakRunner.describe(new SoakRunner.GameResult(3, 12, SoakRunner.Outcome.FAILED, null, 5, 20, 1,
                "rejected: P1 seq 4: WRONG_CARD_TYPE", 1.0));
        assertTrue(failed.contains("FAILED: rejected: P1 seq 4: WRONG_CARD_TYPE"), failed);
        assertTrue(failed.endsWith("(replay: --soak 1 --seed 12)"), failed);
    }

    @Test
    void summary_isOkOnlyWithNoFailures() {
        assertTrue(new SoakRunner.Summary(5, 3, 2, 0).ok(), "turn limits aren't failures");
        assertFalse(new SoakRunner.Summary(5, 4, 0, 1).ok());
    }

    @Test
    @RequiresAssets
    @Timeout(240)
    void realSoak_randomPlayerVsBot_andRandomHotseat_finishWithoutFailures() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

        CliOptions solo = CliOptions.parse(new String[]{"--assets", ASSETS,
                "--p1-deck", "Rogue:2,Warp Mage:1,Fireball:1", "--p2-deck", "Rogue:2,Charger:2"});
        SoakRunner.Summary soloSummary = new SoakRunner(AutoPlayer.Strategy.RANDOM, 120, out)
                .run(3, 100, seed -> solo.startSoakSession(seed, SoakRunner.FAST_SERVER));

        CliOptions hotseat = CliOptions.parse(new String[]{"--mode", "hotseat", "--assets", ASSETS,
                "--p1-deck", "Rogue:2,Warp Mage:1,Fireball:1", "--p2-deck", "Barbarian:2,Rifleman:1,Blizzard:1"});
        SoakRunner.Summary hotseatSummary = new SoakRunner(AutoPlayer.Strategy.RANDOM, 120, out)
                .run(3, 200, seed -> hotseat.startSoakSession(seed, SoakRunner.FAST_SERVER));

        String transcript = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(soloSummary.ok(), transcript);
        assertTrue(hotseatSummary.ok(), transcript);
        assertEquals(3, soloSummary.games());
        assertTrue(transcript.contains("soak: 3 game(s)"), transcript);
    }
}
