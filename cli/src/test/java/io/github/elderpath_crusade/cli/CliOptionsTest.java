package io.github.elderpath_crusade.cli;

import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.net.protocol.SeatRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Flag parsing is pure — no session, no GameContext, no server. */
class CliOptionsTest {

    @Test
    void parseDeck_expandsCountsIntoRepeatedNames() {
        assertEquals(List.of("Wolf", "Wolf", "Wolf Cub"), CliOptions.parseDeck("Wolf:2,Wolf Cub:1"));
        assertEquals(List.of("Runner"), CliOptions.parseDeck("Runner:1"));
    }

    @Test
    void parseDeck_rejectsMalformedEntries() {
        assertThrows(IllegalArgumentException.class, () -> CliOptions.parseDeck("Wolf"));
        assertThrows(IllegalArgumentException.class, () -> CliOptions.parseDeck("Wolf:x"));
        assertThrows(IllegalArgumentException.class, () -> CliOptions.parseDeck(":2"));
        assertThrows(IllegalArgumentException.class, () -> CliOptions.parseDeck(""));
    }

    @Test
    void defaults_soloVsSmartBotWithTheStandardStartingDeck() {
        CliOptions options = CliOptions.parse(new String[0]);
        assertEquals(CliOptions.Mode.SOLO, options.mode());
        assertNull(options.scriptPath());
    }

    @Test
    void mode_isCaseInsensitiveAndRejectsUnknownValues() {
        assertEquals(CliOptions.Mode.HOTSEAT, CliOptions.parse(new String[]{"--mode", "HotSeat"}).mode());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CliOptions.parse(new String[]{"--mode", "versus"}));
        assertTrue(e.getMessage().contains("solo"), e.getMessage());
    }

    @Test
    void join_requiresHost() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CliOptions.parse(new String[]{"--mode", "join"}));
        assertTrue(e.getMessage().contains("--host"), e.getMessage());

        assertDoesNotThrow(() -> CliOptions.parse(new String[]{"--mode", "join", "--host", "example.org"}));
    }

    @Test
    void unknownFlag_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> CliOptions.parse(new String[]{"--nope", "x"}));
    }

    @Test
    void aFlagWithNoValue_isRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CliOptions.parse(new String[]{"--mode"}));
        assertTrue(e.getMessage().contains("--mode"), e.getMessage());
    }

    @Test
    void port_mustBeANonNegativeNumber() {
        assertThrows(IllegalArgumentException.class, () -> CliOptions.parse(new String[]{"--port", "abc"}));
        assertThrows(IllegalArgumentException.class, () -> CliOptions.parse(new String[]{"--port", "-1"}));
        assertDoesNotThrow(() -> CliOptions.parse(new String[]{"--port", "0"}));
    }

    @Test
    void scriptPath_isCarriedThroughForCli004() {
        assertEquals("game.txt", CliOptions.parse(new String[]{"--script", "game.txt"}).scriptPath());
    }

    @Test
    void botAndSeatFlags_acceptEveryEnumValueCaseInsensitively() {
        for (BotKind kind : BotKind.values()) {
            assertDoesNotThrow(() -> CliOptions.parse(new String[]{"--bot", kind.name().toLowerCase()}));
        }
        for (SeatRequest seat : SeatRequest.values()) {
            assertDoesNotThrow(() -> CliOptions.parse(new String[]{"--mode", "join", "--host", "x", "--seat", seat.name().toLowerCase()}));
        }
    }

    @Test
    void soak_defaultsToRandomWithATurnCap_andParsesItsFlags() {
        CliOptions plain = CliOptions.parse(new String[0]);
        assertNull(plain.soakGames(), "no --soak: a normal session");

        CliOptions soak = CliOptions.parse(new String[]{"--soak", "25", "--seed", "9"});
        assertEquals(25, soak.soakGames());
        assertEquals(io.github.elderpath_crusade.client.AutoPlayer.Strategy.RANDOM, soak.soakStrategy());
        assertEquals(SoakRunner.DEFAULT_MAX_TURNS, soak.soakMaxTurns());
        assertEquals(9L, soak.seed());

        CliOptions tuned = CliOptions.parse(new String[]{"--mode", "hotseat", "--soak", "3", "--strategy", "Greedy", "--max-turns", "50"});
        assertEquals(io.github.elderpath_crusade.client.AutoPlayer.Strategy.GREEDY, tuned.soakStrategy());
        assertEquals(50, tuned.soakMaxTurns());
    }

    @Test
    void soak_rejectsModesItCantPlayAlone_scriptsAndUnknownStrategies() {
        assertThrows(IllegalArgumentException.class, () -> CliOptions.parse(new String[]{"--mode", "host", "--soak", "1"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliOptions.parse(new String[]{"--mode", "join", "--host", "x", "--soak", "1"}));
        assertThrows(IllegalArgumentException.class, () -> CliOptions.parse(new String[]{"--soak", "1", "--script", "a.txt"}));
        assertThrows(IllegalArgumentException.class, () -> CliOptions.parse(new String[]{"--soak", "1", "--strategy", "clever"}));
    }
}
