package io.github.elderpath_crusade.utils;

import com.badlogic.gdx.Gdx;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;

public class Logger {
    /** A log file past this size is moved to {@code <name>.1} (replacing an older one) and a fresh one started. */
    static final long MAX_LOG_BYTES = 2L * 1024 * 1024;

    private static final Object FILE_LOCK = new Object();
    private static File logFile;
    private static PrintWriter fileOut;

    public static void log(String tag, String message) {
        if (Gdx.app != null) Gdx.app.log(tag, message);
        else System.out.println("[" + tag + "] " + message);
        writeToFile("INFO", tag, message);
    }

    public static void error(String tag, String message) {
        if (Gdx.app != null) Gdx.app.error(tag, message);
        else System.err.println("[" + tag + "] " + message);
        writeToFile("ERROR", tag, message);
    }

    /** Logs a message and the full stack trace of throwable (to the log file too). */
    public static void error(String tag, String message, Throwable throwable) {
        StringWriter trace = new StringWriter();
        throwable.printStackTrace(new PrintWriter(trace));
        error(tag, message + "\n" + trace);
    }

    /**
     * Also appends every log line to file (creating its folder; the previous run's file is kept as
     * {@code .1} once this one passes 2 MiB). A shipped game has no console, so this file is what a
     * player attaches to a bug report. Only the game client turns it on — the server and the tests
     * keep logging to the console only. Never throws: logging must not be able to break the game.
     */
    public static void enableFileLogging(File file) {
        synchronized (FILE_LOCK) {
            closeFile();
            try {
                File parent = file.getAbsoluteFile().getParentFile();
                if (parent != null) parent.mkdirs();
                if (file.isFile() && file.length() > MAX_LOG_BYTES) {
                    File old = new File(file.getPath() + ".1");
                    old.delete();
                    file.renameTo(old);
                }
                fileOut = new PrintWriter(new FileWriter(file, true), true);
                logFile = file;
            } catch (IOException | RuntimeException e) {
                fileOut = null;
                logFile = null;
            }
        }
    }

    /** Stops writing to the log file (tests). */
    public static void disableFileLogging() {
        synchronized (FILE_LOCK) {
            closeFile();
        }
    }

    /** {@code ~/.elderpathcrusade/logs/game.log} — next to the settings and the deck collection. */
    public static File defaultLogFile() {
        return new File(System.getProperty("user.home"), ".elderpathcrusade/logs/game.log");
    }

    private static void closeFile() {
        if (fileOut != null) fileOut.close();
        fileOut = null;
        logFile = null;
    }

    private static void writeToFile(String level, String tag, String message) {
        synchronized (FILE_LOCK) {
            if (fileOut == null) return;
            try {
                fileOut.println(Instant.now() + " " + level + " [" + tag + "] " + message);
                if (fileOut.checkError() || (logFile != null && logFile.length() > MAX_LOG_BYTES * 2)) closeFile();
            } catch (RuntimeException e) {
                closeFile();
            }
        }
    }
}
