package io.github.forest_of_dreams.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowListener;
import io.github.forest_of_dreams.Main;
import io.github.forest_of_dreams.managers.SettingsManager;

/** Launches the desktop (LWJGL3) application. */
public class Lwjgl3Launcher {
    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return; // This handles macOS support and helps on Windows.
        createApplication();
    }

    private static Lwjgl3Application createApplication() {
        return new Lwjgl3Application(new Main(), getDefaultConfiguration());
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("ForestOfDreams");
        //// Vsync limits the frames per second to what your hardware can display, and helps eliminate
        //// screen tearing. This setting doesn't always work on Linux, so the line after is a safeguard.
        configuration.useVsync(false);
        //// Limits FPS to the refresh rate of the currently active monitor, plus 1 to try to match fractional
        //// refresh rates. The Vsync setting above should limit the actual FPS to match the monitor.
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        //// If you remove the above line and set Vsync to false, you can get unlimited FPS, which can be
        //// useful for testing performance, but can also be very stressful to some hardware.
        //// You may also need to configure GPU drivers to fully disable Vsync; this can cause screen tearing.

        int width = SettingsManager.screenSize.getScreenConfiguredWidth();
        int height = SettingsManager.screenSize.getScreenConfiguredHeight();
        configuration.setResizable(false);  // This prevents window resizing
        configuration.setWindowSizeLimits(width, height, width, height);
        configuration.setWindowedMode(width, height);

        //// Window icon is loaded from the LWJGL3 module resources.
        //// Provide multiple sizes so Linux/GLFW can choose the best match.
        //// If some sizes are missing, the existing ones will be used/scaled.
        //// Place icons in lwjgl3/src/main/resources/ .
        configuration.setWindowIcon(
                // Preferred sizes per your guidance
                "icon-16.png",
                "icon-32.png",
                "icon-64.png",
                "icon-128.png"
        );
        return configuration;
    }
}
