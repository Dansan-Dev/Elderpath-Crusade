package io.github.elderpath_crusade.rooms;

import com.badlogic.gdx.graphics.Color;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;

/** Small shared pieces of the online lobby screens: styled buttons, text lines, centering. */
final class OnlineUi {
    static final int BUTTON_WIDTH = 260;
    static final int BUTTON_HEIGHT = 60;
    static final int FIELD_WIDTH = 460;
    static final int FIELD_HEIGHT = 50;

    private OnlineUi() {}

    static Button button(String label, int width, Runnable onClick) {
        return Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), label, FontType.SILKSCREEN,
                        FontSize.BUTTON_DEFAULT.getSize(), 0, 0, width, BUTTON_HEIGHT, 0)
                .withOnClick((e) -> onClick.run(), ClickableEffectData.getImmediate())
                .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
                .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
                .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
    }

    static Text line(String text, FontSize size, Color color) {
        return new Text(text, FontType.SILKSCREEN, 0, 0, 0, color).withFontSize(size);
    }

    static int centerX() {
        return GameContext.get().getSettingsManager().screenSize.getScreenCenter()[0];
    }

    static int centerY() {
        return GameContext.get().getSettingsManager().screenSize.getScreenCenter()[1];
    }

    /** Horizontally centers a button/field at y (its bottom edge). */
    static void place(Button button, int y) {
        button.getBounds().setX(centerX() - button.getBounds().getWidth() / 2);
        button.getBounds().setY(y);
    }

    /**
     * The connection-state line, centered directly under the screen title. It used to sit at a fixed height
     * above the screen's centre, which put it on top of the title in a 720-pixel-high window (UI-013).
     */
    static void showUnderHeader(Text text, String value, Text header) {
        if (!value.equals(text.getText())) {
            text.setText(value);
            text.update();
        }
        text.getBounds().setX(centerX() - text.getBounds().getWidth() / 2);
        text.getBounds().setY(header.getBounds().getY() - 12 - text.getBounds().getHeight());
    }

    /** Sets a line's text and keeps it horizontally centered at y. */
    static void show(Text text, String value, int y) {
        if (!value.equals(text.getText())) {
            text.setText(value);
            text.update();
        }
        text.getBounds().setX(centerX() - text.getBounds().getWidth() / 2);
        text.getBounds().setY(y);
    }
}
