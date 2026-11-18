package io.github.elderpath_crusade.utils;
import io.github.elderpath_crusade.GameContext;

import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.ui_objects.TextList;

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
        int centerX = GameContext.get().getSettingsManager().screenSize.getScreenCenter()[0];
        int centerY = GameContext.get().getSettingsManager().screenSize.getScreenCenter()[1];
        int screenHeight = GameContext.get().getSettingsManager().screenSize.getScreenHeight();

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
        int centerX = GameContext.get().getSettingsManager().screenSize.getScreenCenter()[0];
        int screenHeight = GameContext.get().getSettingsManager().screenSize.getScreenHeight();
        header.update();
        header.getBounds().setX((int) (centerX - (header.getLabel().getWidth() / 2)));
        header.getBounds().setY(screenHeight - headerTopOffset);
    }

    /**
     * Centers a long one-line flavour/description text under a header (UI-011 follow-up): wrapped to the window's width
     * (minus a margin) and at most two lines high, so a long sentence is shrunk/wrapped instead of running off both
     * sides of the screen. {@code topOffset} is the distance from the top of the window to the text's lower edge.
     */
    public static void centerFlavor(Text text, int topOffset) {
        int screenWidth = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        int screenHeight = GameContext.get().getSettingsManager().screenSize.getScreenHeight();
        int centerX = GameContext.get().getSettingsManager().screenSize.getScreenCenter()[0];
        int wrapWidth = Math.max(200, screenWidth - 160);
        text.withWrapBounds(wrapWidth, 50);
        text.update();
        text.getBounds().setX(centerX - wrapWidth / 2);
        text.getBounds().setY(screenHeight - topOffset);
    }
}
