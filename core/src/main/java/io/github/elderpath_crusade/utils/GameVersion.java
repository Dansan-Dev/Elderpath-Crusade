package io.github.elderpath_crusade.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The running build's version string — {@code gradle.properties}' {@code projectVersion},
 * exposed at runtime via a generated classpath resource ({@code core/build.gradle}'s
 * {@code generateVersionResource} task; nothing read this before Sprint 64's deck collection,
 * whose saved entries record it as {@code game_version}). Falls back to {@link #UNKNOWN} rather
 * than throwing if the resource is missing (e.g. a non-Gradle classpath) — this is metadata, not
 * a domain invariant worth failing fast over.
 */
public final class GameVersion {
    public static final String UNKNOWN = "unknown";
    private static final String RESOURCE = "/elderpath-version.txt";

    private static volatile String cached;

    private GameVersion() {
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
        try (InputStream in = GameVersion.class.getResourceAsStream(RESOURCE)) {
            if (in == null) return UNKNOWN;
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            return text.isEmpty() ? UNKNOWN : text;
        } catch (IOException e) {
            return UNKNOWN;
        }
    }
}
