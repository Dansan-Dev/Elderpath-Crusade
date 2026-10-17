package io.github.elderpath_crusade.server.launcher;

import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.enums.PieceAlignment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LaunchOptionsTest {

    @Test
    void defaults_humanVsSmartOnLoopbackAnyPort() {
        LaunchOptions o = LaunchOptions.parse(new String[0]);
        assertEquals(0, o.port());
        assertEquals("127.0.0.1", o.bind());
        assertEquals(Map.of(PieceAlignment.P2, BotKind.SMART), o.bots());
        assertEquals(List.of(PieceAlignment.P1), o.humanSides());
        assertEquals(List.of("Wolf", "Wolf", "Wolf Cub", "Wolf Cub"), o.decks().get(PieceAlignment.P1));
        assertEquals(o.decks().get(PieceAlignment.P1), o.decks().get(PieceAlignment.P2));
        assertNull(o.seed());
        assertTrue(o.watchStdin());
        assertEquals(60, o.idleTimeoutSeconds());
    }

    @Test
    void parse_everyFlag() {
        LaunchOptions o = LaunchOptions.parse(new String[]{
                "--port", "4567", "--bind", "0.0.0.0", "--p1", "basic", "--p2", "human",
                "--p1-deck", "Rogue:3", "--p2-deck", "Wolf Cub,Fireball:2", "--seed", "42",
                "--assets", "/tmp/a", "--tick-ms", "5", "--time-scale", "20", "--parent-pid", "123",
                "--watch-stdin", "false", "--idle-timeout-s", "7", "--end-grace-s", "0"});
        assertEquals(4567, o.port());
        assertEquals("0.0.0.0", o.bind());
        assertEquals(Map.of(PieceAlignment.P1, BotKind.BASIC), o.bots());
        assertEquals(List.of(PieceAlignment.P2), o.humanSides());
        assertEquals(List.of("Rogue", "Rogue", "Rogue"), o.decks().get(PieceAlignment.P1));
        assertEquals(List.of("Wolf Cub", "Fireball", "Fireball"), o.decks().get(PieceAlignment.P2));
        assertEquals(42L, o.seed());
        assertEquals("/tmp/a", o.assetsDir().getPath());
        assertEquals(5, o.tickMs());
        assertEquals(20f, o.timeScale());
        assertEquals(123L, o.parentPid());
        assertFalse(o.watchStdin());
        assertEquals(7, o.idleTimeoutSeconds());
        assertEquals(0, o.endGraceSeconds());
    }

    @Test
    void botVsBot_hasNoHumanSides() {
        LaunchOptions o = LaunchOptions.parse(new String[]{"--p1", "smart", "--p2", "smart"});
        assertEquals(List.of(), o.humanSides());
    }

    @Test
    void invalidInput_isRejectedWithAMessage() {
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse(new String[]{"--nope", "1"}));
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse(new String[]{"--port"}));
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse(new String[]{"--port", "70000"}));
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse(new String[]{"--p1", "god"}));
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse(new String[]{"--p1-deck", ","}));
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse(new String[]{"--p1-deck", "Wolf:x"}));
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse(new String[]{"--time-scale", "0"}));
    }
}
