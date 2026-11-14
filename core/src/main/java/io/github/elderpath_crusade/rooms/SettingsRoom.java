package io.github.elderpath_crusade.rooms;

import com.badlogic.gdx.Input;
import io.github.elderpath_crusade.enums.settings.InputFunction;
import io.github.elderpath_crusade.enums.settings.Language;
import io.github.elderpath_crusade.enums.settings.RebindResult;
import io.github.elderpath_crusade.input.InputManager;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.config.SettingsManager;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.utils.MenuLayout;

/**
 * FEAT-015/A1: volume steppers and a language selector, on top of the existing "Toggle
 * Fullscreen" control. Every change calls {@link SettingsManager#persist()} (FEAT-015/A2) so it
 * survives the next launch — before this, every setting here reset on every restart, the Deck
 * Collection being the only thing in the project that didn't.
 *
 * FEAT-015/A3 added the two Keybind rows: Pause Menu and Confirm Selection can be rebound to
 * any keyboard key (click Rebind, then press the new key). Exit Game (Q) and Toggle
 * Diagnostics (F3) are deliberately NOT shown here — they're developer-only bindings, not
 * meant to be presented to players as regular keybinds.
 *
 * Layout (reworked 2026-11-15 — the first version's rows weren't aligned to anything, and
 * BUG-054's fix, which positioned each row's button from its own label's width, made that
 * worse by letting the button drift to a different X per row): every row is a fixed four-column
 * grid — label (left-aligned, fixed X), an optional left button, a value (centered in a
 * fixed-width slot), a right button — with every column's X the same across every row
 * regardless of its own content length. The label column's width is measured from the real
 * label Texts at layout time (not guessed), so it always fits whichever label is actually
 * longest ("Confirm Selection") without needing a hand-tuned constant. Rows are grouped under
 * "Audio"/"Display"/"Controls" section headers for visual structure instead of one flat list.
 */
public class SettingsRoom extends Room {
    private static final int HEADER_TOP_OFFSET = 100;
    private static final int TOP_GAP = 210;
    private static final int ROW_SPACING = 52;
    private static final int SECTION_HEADER_GAP = 34;
    private static final int SECTION_GAP = 30;
    private static final int STEP_BUTTON_SIZE = 50;
    private static final int WIDE_BUTTON_WIDTH = 140;
    private static final int WIDE_BUTTON_HEIGHT = 50;
    private static final int COLUMN_GAP = 18;
    private static final int VALUE_COLUMN_WIDTH = 280;

    private Text header;
    private Text audioHeader;
    private Text displayHeader;
    private Text controlsHeader;

    private VolumeRow masterRow;
    private VolumeRow musicRow;
    private VolumeRow sfxRow;
    private SimpleRow languageRow;
    private SimpleRow fullscreenRow;
    private KeybindRow pauseMenuRow;
    private KeybindRow confirmSelectionRow;
    private Button backButton;

    // Grid columns, recomputed in layoutContents() from the real label widths.
    private int gridLabelX;
    private int gridLeftButtonX;
    private int gridValueCenterX;
    private int gridRightButtonX;

    private SettingsRoom() {
        super();
        SettingsManager settings = GameContext.get().getSettingsManager();
        // Defensive: a capture should never survive leaving this room (see backButton below),
        // but guard against entering fresh with one still dangling regardless.
        GameContext.get().getInputManager().cancelCapture();

        GameContext.get().getMusicManager().playLoopingMusic("Evening_Harmony.mp3");

        header = new Text("Settings", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
            .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        audioHeader = sectionHeader("Audio");
        masterRow = new VolumeRow("Master Volume", settings.sound::getMasterVolume, v -> {
            settings.sound.setMasterVolume(v);
            settings.persist();
        });
        musicRow = new VolumeRow("Music Volume", settings.sound::getMusicVolume, v -> {
            settings.sound.setMusicVolume(v);
            settings.persist();
        });
        sfxRow = new VolumeRow("SFX Volume", settings.sound::getSFXVolume, v -> {
            settings.sound.setSFXVolume(v);
            settings.persist();
        });

        displayHeader = sectionHeader("Display");
        languageRow = buildLanguageRow(settings);
        fullscreenRow = buildFullscreenRow(settings);

        controlsHeader = sectionHeader("Controls");
        pauseMenuRow = new KeybindRow("Pause Menu", InputFunction.PAUSE_MENU);
        confirmSelectionRow = new KeybindRow("Confirm Selection", InputFunction.CONFIRM_SELECTION);

        backButton = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Back", FontType.SILKSCREEN, FontSize.BUTTON_DEFAULT.getSize(), 0, 0, 120, 60, 0)
            .withOnClick((e) -> {
                // A pending capture must never outlive this room — the next keypress anywhere
                // else in the game would otherwise silently rebind it.
                GameContext.get().getInputManager().cancelCapture();
                GameContext.get().getRoomManager().gotoRoom(MainMenuRoom::get);
            }, ClickableEffectData.getImmediate())
            .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
            .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
            .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
        addContent(backButton);

        layoutContents();
    }

    // --- shared element factories ---

    private Text sectionHeader(String text) {
        Text t = new Text(text, FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
            .withFontSize(FontSize.BODY_LARGE);
        addContent(t);
        return t;
    }

    private Text rowText(String text) {
        Text t = new Text(text, FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
            .withFontSize(FontSize.BODY_MEDIUM);
        addContent(t);
        return t;
    }

    private Button stepButton(String label, Runnable onClick) {
        Button button = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), label, FontType.SILKSCREEN,
                FontSize.BUTTON_DEFAULT.getSize(), 0, 0, STEP_BUTTON_SIZE, STEP_BUTTON_SIZE, 0)
            .withOnClick((e) -> onClick.run(), ClickableEffectData.getImmediate())
            .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
            .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
            .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
        addContent(button);
        return button;
    }

    private Button wideButton(String label, Runnable onClick) {
        Button button = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), label, FontType.SILKSCREEN,
                FontSize.BUTTON_DEFAULT.getSize(), 0, 0, WIDE_BUTTON_WIDTH, WIDE_BUTTON_HEIGHT, 0)
            .withOnClick((e) -> onClick.run(), ClickableEffectData.getImmediate())
            .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
            .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
            .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
        addContent(button);
        return button;
    }

    private SimpleRow buildLanguageRow(SettingsManager settings) {
        Button prev = stepButton("<", () -> cycleLanguage(settings, -1));
        Button next = stepButton(">", () -> cycleLanguage(settings, 1));
        return new SimpleRow("Language", settings.language.getLanguage().name(), prev, next);
    }

    private SimpleRow buildFullscreenRow(SettingsManager settings) {
        Button toggle = wideButton("Toggle", () -> {
            settings.screenSize.toggleFullscreen();
            settings.persist();
            fullscreenRow.setValue(fullscreenText(settings));
        });
        return new SimpleRow("Fullscreen", fullscreenText(settings), null, toggle);
    }

    private static String fullscreenText(SettingsManager settings) {
        return settings.screenSize.isFullscreen() ? "On" : "Off";
    }

    private void cycleLanguage(SettingsManager settings, int direction) {
        Language[] values = Language.values();
        int currentIndex = settings.language.getLanguage().ordinal();
        int nextIndex = Math.floorMod(currentIndex + direction, values.length);
        settings.language.setLanguage(values[nextIndex]);
        settings.persist();
        languageRow.setValue(settings.language.getLanguage().name());
    }

    /** Centers `value` in the fixed value column; call after any change to its text. */
    private void centerValue(Text value) {
        if (value == null) return;
        value.getBounds().setX(gridValueCenterX - value.getWidth() / 2);
    }

    /** One grid row with a label, an optional left button, a centered value, and a right button. */
    private class SimpleRow {
        private final Text label;
        private final Text value;
        private final Button leftButton;
        private final Button rightButton;

        SimpleRow(String labelText, String valueText, Button leftButton, Button rightButton) {
            this.label = rowText(labelText);
            this.value = valueText != null ? rowText(valueText) : null;
            this.leftButton = leftButton;
            this.rightButton = rightButton;
        }

        void setValue(String text) {
            if (value == null) return;
            value.setText(text);
            value.update();
            centerValue(value);
        }

        void layout(int y) {
            label.update();
            label.getBounds().setX(gridLabelX);
            label.getBounds().setY(y);
            if (leftButton != null) {
                leftButton.getBounds().setX(gridLeftButtonX);
                leftButton.getBounds().setY(y - 10);
            }
            if (value != null) {
                value.update();
                value.getBounds().setY(y);
                centerValue(value);
            }
            rightButton.getBounds().setX(gridRightButtonX);
            rightButton.getBounds().setY(y - 10);
        }
    }

    /** One volume row — label, current value, and +/- steppers; reused for Master/Music/SFX. */
    private class VolumeRow {
        private final SimpleRow row;
        private final java.util.function.IntSupplier getter;
        private final java.util.function.IntConsumer setter;

        VolumeRow(String name, java.util.function.IntSupplier getter, java.util.function.IntConsumer setter) {
            this.getter = getter;
            this.setter = setter;
            Button decrement = stepButton("-", () -> change(-1));
            Button increment = stepButton("+", () -> change(1));
            this.row = new SimpleRow(name, String.valueOf(getter.getAsInt()), decrement, increment);
        }

        private void change(int delta) {
            int clamped = Math.max(1, Math.min(10, getter.getAsInt() + delta));
            setter.accept(clamped);
            row.setValue(String.valueOf(clamped));
        }

        void layout(int y) {
            row.layout(y);
        }
    }

    /**
     * One rebindable keybind row (FEAT-015/A3) — label, the function's current key as the row's
     * value, and a Rebind button that starts listening for the next physical key press. While
     * THIS row is listening, clicking its own button again cancels; clicking the OTHER row's
     * button is a no-op (only one capture can be in flight at a time — InputManager tracks a
     * single capturingFunction) rather than silently stealing it and leaving this row's value
     * stuck on "press any key...".
     */
    private class KeybindRow {
        private final InputFunction function;
        private final SimpleRow row;

        KeybindRow(String displayName, InputFunction function) {
            this.function = function;
            Button rebind = wideButton("Rebind", this::onRebindClicked);
            this.row = new SimpleRow(displayName, idleValue(), null, rebind);
        }

        private void onRebindClicked() {
            InputManager inputManager = GameContext.get().getInputManager();
            if (inputManager.isCapturing()) {
                if (inputManager.getCapturingFunction() == function) {
                    inputManager.cancelCapture();
                    row.setValue(idleValue());
                }
                return; // a different row is listening — ignore this click rather than steal it
            }
            inputManager.startCapture(function, this::onCaptured);
            row.setValue("press any key...");
        }

        private void onCaptured(RebindResult result) {
            row.setValue(result == RebindResult.OK ? idleValue() : idleValue() + " (in use)");
        }

        private String idleValue() {
            int keycode = GameContext.get().getSettingsManager().keybinds.getKeycode(function);
            return Input.Keys.toString(keycode);
        }

        void layout(int y) {
            row.layout(y);
        }
    }

    private void layoutContents() {
        int[] screenCenter = GameContext.get().getSettingsManager().screenSize.getScreenCenter();
        int screenCenterX = screenCenter[0];
        int screenCenterY = screenCenter[1];

        MenuLayout.centerHeader(header, HEADER_TOP_OFFSET);
        computeGridColumns(screenCenterX);

        int y = screenCenterY + TOP_GAP;

        y = layoutSectionHeader(audioHeader, y);
        masterRow.layout(y); y -= ROW_SPACING;
        musicRow.layout(y); y -= ROW_SPACING;
        sfxRow.layout(y); y -= SECTION_GAP;

        y = layoutSectionHeader(displayHeader, y);
        languageRow.layout(y); y -= ROW_SPACING;
        fullscreenRow.layout(y); y -= SECTION_GAP;

        y = layoutSectionHeader(controlsHeader, y);
        pauseMenuRow.layout(y); y -= ROW_SPACING;
        confirmSelectionRow.layout(y); y -= SECTION_GAP;

        backButton.getBounds().setX(screenCenterX - backButton.getBounds().getWidth() / 2);
        backButton.getBounds().setY(y);
    }

    /** Positions a section header at `y` (aligned with the row labels below it) and returns the y for its first row. */
    private int layoutSectionHeader(Text sectionHeader, int y) {
        sectionHeader.update();
        sectionHeader.getBounds().setX(gridLabelX);
        sectionHeader.getBounds().setY(y);
        return y - SECTION_HEADER_GAP;
    }

    /**
     * Every row shares the same four columns — label, left button, value, right button — so
     * nothing drifts per row the way BUG-054's per-row fix did. The label column's width is
     * measured from the real label Texts (not a guessed constant), so it always fits whichever
     * label is actually longest without needing to be hand-tuned when a label changes.
     */
    private void computeGridColumns(int screenCenterX) {
        int labelColumnWidth = maxLabelWidth();
        int totalWidth = labelColumnWidth + COLUMN_GAP + STEP_BUTTON_SIZE + COLUMN_GAP
                + VALUE_COLUMN_WIDTH + COLUMN_GAP + STEP_BUTTON_SIZE;

        gridLabelX = screenCenterX - totalWidth / 2;
        gridLeftButtonX = gridLabelX + labelColumnWidth + COLUMN_GAP;
        gridValueCenterX = gridLeftButtonX + STEP_BUTTON_SIZE + COLUMN_GAP + VALUE_COLUMN_WIDTH / 2;
        gridRightButtonX = gridLeftButtonX + STEP_BUTTON_SIZE + COLUMN_GAP + VALUE_COLUMN_WIDTH + COLUMN_GAP;
    }

    private int maxLabelWidth() {
        int max = 0;
        for (Text label : new Text[] {
                masterRow.row.label, musicRow.row.label, sfxRow.row.label,
                languageRow.label, fullscreenRow.label,
                pauseMenuRow.row.label, confirmSelectionRow.row.label
        }) {
            label.update();
            max = Math.max(max, label.getWidth());
        }
        return max;
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static SettingsRoom get() {
        return new SettingsRoom();
    }
}
