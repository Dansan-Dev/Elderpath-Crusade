package io.github.elderpath_crusade.client;

import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.run.RunCardModification;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The LAUNCH-001 half of the client→battle-launch boundary — relics/card-mods carried into launcherArgs(). */
class LocalMatchSpecTest {

    @Test
    void solo_hasNoRelicsOrCardModsOrCardLockTurnsByDefault() {
        LocalMatchSpec spec = LocalMatchSpec.solo(List.of("Wolf"), List.of("Wolf"), BotKind.SMART);
        assertEquals(Map.of(), spec.relics());
        assertEquals(Map.of(), spec.cardMods());
        assertEquals(Map.of(), spec.cardLockTurns());
        assertFalse(String.join(" ", spec.launcherArgs()).contains("relics"));
        assertFalse(String.join(" ", spec.launcherArgs()).contains("card-mods"));
        assertFalse(String.join(" ", spec.launcherArgs()).contains("card-lock-turns"));
    }

    @Test
    void withCardLockTurns_addsThePNCardLockTurnsFlagForThatSideOnly() {
        LocalMatchSpec spec = LocalMatchSpec.solo(List.of("Wolf"), List.of("Wolf"), BotKind.SMART)
                .withCardLockTurns(PieceAlignment.P1, 3);

        List<String> args = spec.launcherArgs();
        assertEquals("3", valueAfter(args, "--p1-card-lock-turns"));
        assertFalse(args.contains("--p2-card-lock-turns"));
    }

    @Test
    void withCardLockTurns_ofZero_addsNoFlag() {
        LocalMatchSpec spec = LocalMatchSpec.solo(List.of("Wolf"), List.of("Wolf"), BotKind.SMART)
                .withCardLockTurns(PieceAlignment.P1, 0);

        assertFalse(String.join(" ", spec.launcherArgs()).contains("card-lock-turns"));
    }

    @Test
    void withRelics_addsThePNRelicsFlagForThatSideOnly() {
        LocalMatchSpec spec = LocalMatchSpec.solo(List.of("Wolf"), List.of("Wolf"), BotKind.SMART)
                .withRelics(PieceAlignment.P1, List.of("PackLeader", "Bonfire"));

        List<String> args = spec.launcherArgs();
        assertEquals("PackLeader,Bonfire", valueAfter(args, "--p1-relics"));
        assertFalse(args.contains("--p2-relics"));
    }

    @Test
    void withCardMods_addsThePNCardModsFlagForThatSideOnly() {
        LocalMatchSpec spec = LocalMatchSpec.solo(List.of("Wolf"), List.of("Wolf"), BotKind.SMART)
                .withCardMods(PieceAlignment.P1,
                        Map.of("run-0", new RunCardModification(Map.of("damage", 2), List.of("Frenzy"))));

        List<String> args = spec.launcherArgs();
        assertEquals("run-0@damage=2:Frenzy", valueAfter(args, "--p1-card-mods"));
        assertFalse(args.contains("--p2-card-mods"));
    }

    @Test
    void withRelics_isReplaceNotAccumulate() {
        LocalMatchSpec spec = LocalMatchSpec.solo(List.of("Wolf"), List.of("Wolf"), BotKind.SMART)
                .withRelics(PieceAlignment.P1, List.of("PackLeader"))
                .withRelics(PieceAlignment.P1, List.of("Bonfire"));

        assertEquals(List.of("Bonfire"), spec.relics().get(PieceAlignment.P1));
    }

    private static String valueAfter(List<String> args, String flag) {
        int i = args.indexOf(flag);
        assertTrue(i >= 0, flag + " not found in " + args);
        return args.get(i + 1);
    }
}
