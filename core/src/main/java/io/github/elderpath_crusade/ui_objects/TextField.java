package io.github.elderpath_crusade.ui_objects;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.input.TextEntry;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;

/**
 * A one-line text box: click it to type into its {@link TextEntry}, Enter or ESC (or editing
 * another field) to stop. Drawn as a Button whose label is the entry's text (bullets when
 * masked), a blinking caret while focused, and a placeholder while empty and unfocused. Typing
 * itself is handled by InputManager, which also keeps hotkeys (Q, ESC) from firing meanwhile.
 */
public class TextField extends Button {
    private static final long CARET_BLINK_MS = 500;

    private final TextEntry entry;
    private final String placeholder;
    private String shown;
    // The blinking caret is a separate label drawn after the text, never part of it: the button centers
    // its text, so a caret inside the string moved the whole name sideways every blink (UI-012).
    private Text caret;

    private TextField(TextEntry entry, String placeholder, Runnable onSubmit, int x, int y, int width, int height, int z) {
        super(placeholder, FontType.SILKSCREEN, FontSize.BUTTON_DEFAULT.getSize(), x, y, width, height, z);
        this.entry = entry;
        this.placeholder = placeholder;
        this.shown = placeholder;
        setBackgroundColor(ColorSettings.BUTTON_PRIMARY.getColor());
        withHoverColor(ColorSettings.BUTTON_HOVER.getColor());
        withBorderColor(ColorSettings.BUTTON_BORDER.getColor());
        withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
        withOnClick((e) -> GameContext.get().getInputManager().startTextEditing(entry, onSubmit, null),
                ClickableEffectData.getImmediate());
    }

    /** onSubmit (may be null) runs when the player presses Enter in this field. */
    public static TextField create(TextEntry entry, String placeholder, Runnable onSubmit,
                                   int x, int y, int width, int height, int z) {
        return new TextField(entry, placeholder, onSubmit, x, y, width, height, z);
    }

    public TextEntry getEntry() {
        return entry;
    }

    private boolean focused() {
        return GameContext.get().getInputManager().isEditing(entry);
    }

    private boolean caretOn() {
        return focused() && (System.currentTimeMillis() / CARET_BLINK_MS) % 2 == 0;
    }

    private void refreshLabel() {
        boolean focused = focused();
        String text = entry.isEmpty() && !focused ? placeholder : entry.display();
        setBorderColor((focused ? ColorSettings.BUTTON_BORDER_HOVER : ColorSettings.BUTTON_BORDER).getColor());
        if (text.equals(shown)) return;
        shown = text;
        setText(text);
        if (textObj != null) {
            textObj.setText(text);
            textObj.update();
        }
    }

    /** Draws the caret just after the (already centered) text; boxX/boxY is the field's bottom-left corner. */
    private void drawCaret(SpriteBatch batch, int boxX, int boxY) {
        if (!caretOn() || textObj == null) return;
        if (caret == null) {
            caret = new Text("_", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                    .withFontSize(FontSize.BUTTON_DEFAULT);
        }
        caret.update();
        if (caret.getLabel() == null) return;
        boolean hasText = !entry.isEmpty();
        int textRight = hasText
                ? textObj.getBounds().getX() + textObj.getBounds().getWidth()
                : boxX + getWidth() / 2;
        int caretY = boxY + Math.round((getHeight() - caret.getLabel().getPrefHeight()) / 2f);
        caret.getBounds().setX(textRight);
        caret.getBounds().setY(caretY);
        caret.update();
        caret.getLabel().draw(batch, 1);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        refreshLabel();
        super.render(batch, zLevel, isPaused);
        if (!isPaused && zLevel == getZs().get(0)) drawCaret(batch, getX(), getY());
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        refreshLabel();
        super.render(batch, zLevel, isPaused, x, y);
        if (!isPaused && zLevel == getZs().get(0)) drawCaret(batch, x, y);
    }
}
