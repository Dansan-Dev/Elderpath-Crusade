package io.github.forest_of_dreams.utils;

import com.badlogic.gdx.Gdx;

public class Logger {
    public static void log(String tag, String message) {
        Gdx.app.log(tag, message);
    }

    public static void error(String tag, String message) {
        Gdx.app.error(tag, message);
    }
}
