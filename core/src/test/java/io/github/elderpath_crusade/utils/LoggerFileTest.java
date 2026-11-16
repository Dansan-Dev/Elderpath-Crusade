package io.github.elderpath_crusade.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class LoggerFileTest {
    @AfterEach
    void tearDown() {
        Logger.disableFileLogging();
    }

    @Test
    void linesAreAppendedWithLevelTagAndTimestamp(@TempDir File dir) throws Exception {
        File file = new File(dir, "logs/game.log"); // the folder doesn't exist yet
        Logger.enableFileLogging(file);
        Logger.log("Main", "hello");
        Logger.error("Net", "it broke");
        String text = Files.readString(file.toPath());
        assertTrue(text.contains("INFO [Main] hello"), text);
        assertTrue(text.contains("ERROR [Net] it broke"), text);
        assertTrue(text.matches("(?s)\\d{4}-\\d{2}-\\d{2}T.*"), "starts with an ISO timestamp: " + text);
    }

    @Test
    void aThrowableLogsItsStackTrace(@TempDir File dir) throws Exception {
        File file = new File(dir, "game.log");
        Logger.enableFileLogging(file);
        Logger.error("Crash", "boom", new IllegalStateException("bad state"));
        String text = Files.readString(file.toPath());
        assertTrue(text.contains("IllegalStateException: bad state"), text);
        assertTrue(text.contains("LoggerFileTest"), "the trace names the calling code");
    }

    @Test
    void nothingIsWrittenUnlessEnabled_andDisablingStopsIt(@TempDir File dir) throws Exception {
        File file = new File(dir, "game.log");
        Logger.log("A", "before");
        assertFalse(file.exists());
        Logger.enableFileLogging(file);
        Logger.log("A", "during");
        Logger.disableFileLogging();
        Logger.log("A", "after");
        String text = Files.readString(file.toPath());
        assertTrue(text.contains("during"));
        assertFalse(text.contains("before"));
        assertFalse(text.contains("after"));
    }

    @Test
    void anOversizedLogIsMovedAsideOnNextStart(@TempDir File dir) throws Exception {
        File file = new File(dir, "game.log");
        Files.write(file.toPath(), new byte[(int) Logger.MAX_LOG_BYTES + 10]);
        Logger.enableFileLogging(file);
        Logger.log("A", "fresh");
        assertTrue(new File(dir, "game.log.1").isFile(), "the big one was kept as .1");
        assertTrue(file.length() < 1000, "a fresh file was started");
    }

    @Test
    void anUnwritableLocationNeverThrows(@TempDir File dir) throws Exception {
        File blocker = new File(dir, "afile");
        Files.writeString(blocker.toPath(), "x");
        assertDoesNotThrow(() -> Logger.enableFileLogging(new File(blocker, "sub/game.log"))); // parent is a file
        assertDoesNotThrow(() -> Logger.log("A", "still fine"));
    }
}
