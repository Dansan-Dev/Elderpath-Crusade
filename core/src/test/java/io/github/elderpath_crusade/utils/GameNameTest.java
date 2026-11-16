package io.github.elderpath_crusade.utils;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/** The name the game shows comes from gradle.properties' projectName through a generated resource. */
class GameNameTest {
    @Test
    void currentIsTheNameFromGradleProperties() throws Exception {
        Properties properties = new Properties();
        // tests run from the module directory (core/); gradle.properties is one level up
        File file = new File("../gradle.properties");
        assertTrue(file.isFile(), "expected " + file.getAbsolutePath());
        properties.load(Files.newBufferedReader(file.toPath()));
        assertEquals(properties.getProperty("projectName"), GameName.current());
        assertNotEquals(GameName.FALLBACK + "!", GameName.current());
    }

    @Test
    void isCached() {
        assertSame(GameName.current(), GameName.current());
    }
}
