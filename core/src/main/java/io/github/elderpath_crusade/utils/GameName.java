package io.github.elderpath_crusade.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The game's display name — {@code gradle.properties}' {@code projectName}, the single source for
 * the window title, the main menu header and the log header (and, with the spaces removed, the
 * jar/executable names in build.gradle). Exposed at runtime through a generated classpath
 * resource, exactly like {@link GameVersion}. Falls back to {@link #FALLBACK} if the resource is
 * missing (a non-Gradle classpath) rather than failing — it is a label, not an invariant.
 */
public final class GameName {
    public static final String FALLBACK = "Elderpath Crusade";
    private static final String RESOURCE = "/elderpath-name.txt";

    private static volatile String cached;

    private GameName() {
    }

    public static String current() {
        String value = cached;
        if (value == null) {
            value = read();
            cached = value;
        }
        return value;
    }

    private static String read() {
        try (InputStream in = GameName.class.getResourceAsStream(RESOURCE)) {
            if (in == null) return FALLBACK;
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            return text.isEmpty() ? FALLBACK : text;
        } catch (IOException e) {
            return FALLBACK;
        }
    }
}
