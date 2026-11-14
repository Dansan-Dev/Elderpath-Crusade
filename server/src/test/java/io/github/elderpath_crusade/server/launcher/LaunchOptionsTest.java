package io.github.elderpath_crusade.server.launcher;

import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.data.AbilityRef;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.run.RunCardModification;
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
        assertNull(o.assetsDir(), "no --assets: working directory, else the classpath");
        assertTrue(o.watchStdin());
        assertEquals(60, o.idleTimeoutSeconds());
        assertEquals(List.of(), o.relics().get(PieceAlignment.P1), "no --p1-relics: no relics");
        assertEquals(Map.of(), o.cardMods().get(PieceAlignment.P1), "no --p1-card-mods: no modifications");
        assertEquals(0, o.cardLockTurns().get(PieceAlignment.P1), "no --p1-card-lock-turns: never locked");
        assertEquals(List.of(), o.elders().get(PieceAlignment.P1), "no --p1-elders: no elder pieces");
        assertEquals(0, o.healthPort(), "no --health-port: health checks disabled");
    }

    @Test
    void parse_relicsAndCardMods() {
        LaunchOptions o = LaunchOptions.parse(new String[]{
                "--p1-relics", "PackLeader,Bonfire",
                "--p1-card-mods", "run-3@damage=2:Frenzy"});
        assertEquals(List.of("PackLeader", "Bonfire"), o.relics().get(PieceAlignment.P1));
        assertEquals(List.of(), o.relics().get(PieceAlignment.P2), "no --p2-relics: no relics");
        assertEquals(Map.of("run-3", new RunCardModification(Map.of("damage", 2), List.of(new AbilityRef("Frenzy")))),
                o.cardMods().get(PieceAlignment.P1));
        assertEquals(Map.of(), o.cardMods().get(PieceAlignment.P2), "no --p2-card-mods: no modifications");
    }

    @Test
    void parse_cardLockTurns() {
        LaunchOptions o = LaunchOptions.parse(new String[]{"--p1-card-lock-turns", "3"});
        assertEquals(3, o.cardLockTurns().get(PieceAlignment.P1));
        assertEquals(0, o.cardLockTurns().get(PieceAlignment.P2), "no --p2-card-lock-turns: never locked");
    }

    @Test
    void parse_personalityFlavoredSmartBotKinds() {
        LaunchOptions o = LaunchOptions.parse(new String[]{"--p1", "smart_aggressive", "--p2", "smart_defensive"});
        assertEquals(BotKind.SMART_AGGRESSIVE, o.bots().get(PieceAlignment.P1));
        assertEquals(BotKind.SMART_DEFENSIVE, o.bots().get(PieceAlignment.P2));
    }

    @Test
    void parse_elders() {
        LaunchOptions o = LaunchOptions.parse(new String[]{"--p1-elders", "Warp Mage,Wolf Cub#run-3"});
        assertEquals(List.of("Warp Mage", "Wolf Cub#run-3"), o.elders().get(PieceAlignment.P1));
        assertEquals(List.of(), o.elders().get(PieceAlignment.P2), "no --p2-elders: no elder pieces");
    }

    @Test
    void parse_everyFlag() {
        LaunchOptions o = LaunchOptions.parse(new String[]{
                "--port", "4567", "--bind", "0.0.0.0", "--p1", "basic", "--p2", "human",
                "--p1-deck", "Rogue:3", "--p2-deck", "Wolf Cub,Fireball:2", "--seed", "42",
                "--assets", "/tmp/a", "--tick-ms", "5", "--time-scale", "20", "--parent-pid", "123",
                "--watch-stdin", "false", "--idle-timeout-s", "7", "--end-grace-s", "0", "--health-port", "8080"});
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
        assertEquals(8080, o.healthPort());
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
