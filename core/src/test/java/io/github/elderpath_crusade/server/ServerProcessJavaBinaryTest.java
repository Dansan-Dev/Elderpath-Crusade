package io.github.elderpath_crusade.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import org.junit.jupiter.api.Test;

/** A packaged build's process command is the native launcher, so the child server must use java.home's java. */
class ServerProcessJavaBinaryTest {

    @Test
    void javaBinary_javaHomeHasExecutable_usesIt() {
        String javaHome = System.getProperty("java.home");
        String expected = javaHome + File.separator + "bin" + File.separator
                + (System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java");
        assertTrue(new File(expected).isFile(), "test JVM should have a java executable");
        assertEquals(expected, ServerProcess.javaBinary());
    }
}
