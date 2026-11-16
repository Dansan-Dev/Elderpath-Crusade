package io.github.elderpath_crusade.server.launcher;

import io.github.elderpath_crusade.lobby.MatchLauncher;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcessMatchLauncherTest {

    @Test
    void deckArgument_countsDuplicatesInFirstSeenOrder() throws IOException {
        assertEquals("Wolf:2,Crow:1,Wolf Cub:1", ProcessMatchLauncher.deckArgument(List.of("Wolf", "Crow", "Wolf", "Wolf Cub")));
    }

    @Test
    void deckArgument_refusesNamesTheFlagSyntaxCannotCarry() {
        for (String bad : new String[]{"Wolf,Crow", "Wolf:9", "", " ", null}) {
            assertThrows(IOException.class, () -> ProcessMatchLauncher.deckArgument(java.util.Arrays.asList("Wolf", bad)), String.valueOf(bad));
        }
    }

    @Test
    void arguments_makeBothSeatsHumanOnLocalhostWithBothDecksAndTheSeed() throws IOException {
        ProcessMatchLauncher launcher = new ProcessMatchLauncher(new File("/some/assets"));
        List<String> args = launcher.arguments(new MatchLauncher.MatchSpec(List.of("Wolf"), List.of("Crow", "Crow"), 42L));
        assertEquals("127.0.0.1", args.get(args.indexOf("--bind") + 1));
        assertEquals("human", args.get(args.indexOf("--p1") + 1));
        assertEquals("human", args.get(args.indexOf("--p2") + 1));
        assertEquals("42", args.get(args.indexOf("--seed") + 1));
        assertEquals("Wolf:1", args.get(args.indexOf("--p1-deck") + 1));
        assertEquals("Crow:2", args.get(args.indexOf("--p2-deck") + 1));
        assertEquals(new File("/some/assets").getAbsolutePath(), args.get(args.indexOf("--assets") + 1));
        launcher.close();
    }

    @Test
    void arguments_withoutAnAssetsDirReadTheClasspath() throws IOException {
        ProcessMatchLauncher launcher = new ProcessMatchLauncher(null);
        assertFalse(launcher.arguments(new MatchLauncher.MatchSpec(List.of("Wolf"), List.of("Wolf"), 1L)).contains("--assets"));
        launcher.close();
    }
}
