package io.github.forest_of_dreams.utils;

import io.github.forest_of_dreams.ui_objects.Text;
import io.github.forest_of_dreams.ui_objects.TextList;
import io.github.forest_of_dreams.managers.SettingsManager;

/**
 * Small helper to centralize common menu layout patterns (header centering + vertical options list).
 * This reduces duplicated code across Pause menu pages and keeps spacing consistent.
 */
public final class MenuLayout {
    private MenuLayout() {}

    /**
     * Centers the header at the top with a fixed offset from the top edge and vertically aligns
     * the options around screen center with the given spacing.
     *
     * @param header            The header Text object
     * @param options           The TextList containing option items
     * @param spacing           Vertical spacing between options in pixels
     * @param headerTopOffset   Distance from screen top to place the header baseline in pixels
     */
    public static void layoutHeaderAndOptions(Text header, TextList options, int spacing, int headerTopOffset) {
        int centerX = SettingsManager.screenSize.getScreenCenter()[0];
        int centerY = SettingsManager.screenSize.getScreenCenter()[1];
        int screenHeight = SettingsManager.screenSize.getScreenHeight();

        // Update header to ensure label dimensions are current before centering
        header.update();
        header.getBounds().setX((int) (centerX - (header.getLabel().getWidth() / 2)));
        header.getBounds().setY(screenHeight - headerTopOffset);

        // Align options around vertical center
        options.alignTextAcrossYAxis(spacing, centerX, centerY);
    }

    /**
     * Centers a header Text at a fixed offset from the top of the screen.
     * Useful when a screen has a single header without an options list.
     */
    public static void centerHeader(Text header, int headerTopOffset) {
        int centerX = SettingsManager.screenSize.getScreenCenter()[0];
        int screenHeight = SettingsManager.screenSize.getScreenHeight();
        header.update();
        header.getBounds().setX((int) (centerX - (header.getLabel().getWidth() / 2)));
        header.getBounds().setY(screenHeight - headerTopOffset);
    }
}
