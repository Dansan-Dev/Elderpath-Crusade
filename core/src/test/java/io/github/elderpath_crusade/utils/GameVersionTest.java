package io.github.elderpath_crusade.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link GameVersion} reads core/build.gradle's generated {@code elderpath-version.txt}
 * resource — this test runs through Gradle, so that resource is always on the classpath here
 * (processResources runs before testClasses); it just isn't blank or the fallback.
 */
class GameVersionTest {

    @Test
    void current_readsTheGeneratedVersionResource() {
        String version = GameVersion.current();
        assertNotNull(version);
        assertFalse(version.isBlank());
        assertNotEquals(GameVersion.UNKNOWN, version, "the generated resource should be on the test classpath");
    }

    @Test
    void current_isCachedAcrossCalls() {
        assertSame(GameVersion.current(), GameVersion.current());
    }
}
