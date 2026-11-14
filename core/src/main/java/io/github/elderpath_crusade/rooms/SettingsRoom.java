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
 * FEAT-015/A1: volume sliders (as +/- steppers, matching this codebase's existing button-pair
 * convention rather than a new drag-slider widget) and a language selector, on top of the one
 * existing "Toggle Fullscreen" control. Every change calls {@link SettingsManager#persist()}
 * (FEAT-015/A2) so it survives the next launch — before this, every setting here reset on every
 * restart, the Deck Collection being the only thing in the project that didn't.
 *
 * FEAT-015/A3 added the two Keybind rows below the language selector: Pause Menu and Confirm
 * Selection can be rebound to any keyboard key (click Rebind, then press the new key). Exit
 * Game (Q) and Toggle Diagnostics (F3) are deliberately NOT shown here — they're
 * developer-only bindings, not meant to be presented to players as regular keybinds.
 */
public class SettingsRoom extends Room {
    private static final int ROW_SPACING = 70;
    private static final int STEP_BUTTON_SIZE = 50;
    private static final int LABEL_WIDTH = 260;
    private static final int VALUE_GAP = 20;
    private static final int KEYBIND_BUTTON_WIDTH = 140;
    private static final int KEYBIND_BUTTON_HEIGHT = 50;

    private Text header;
    private VolumeRow masterRow;
    private VolumeRow musicRow;
    private VolumeRow sfxRow;
    private Text languageLabel;
    private Button languagePrev;
    private Button languageNext;
    private KeybindRow pauseMenuRow;
    private KeybindRow confirmSelectionRow;
    private Button toggleFullscreen;
    private Button backButton;

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

        languageLabel = new Text(languageText(settings), FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
            .withFontSize(FontSize.BODY_MEDIUM);
        addContent(languageLabel);
        languagePrev = stepButton("<", () -> cycleLanguage(settings, -1));
        languageNext = stepButton(">", () -> cycleLanguage(settings, 1));
        addContent(languagePrev);
        addContent(languageNext);

        pauseMenuRow = new KeybindRow("Pause Menu", InputFunction.PAUSE_MENU);
        confirmSelectionRow = new KeybindRow("Confirm Selection", InputFunction.CONFIRM_SELECTION);

        toggleFullscreen = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Toggle Fullscreen", FontType.SILKSCREEN, FontSize.BUTTON_DEFAULT.getSize(), 0, 0, 200, 60, 0)
            .withOnClick((e) -> {
                settings.screenSize.toggleFullscreen();
                settings.persist();
            }, ClickableEffectData.getImmediate())
            .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
            .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
            .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
        addContent(toggleFullscreen);

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

    private void cycleLanguage(SettingsManager settings, int direction) {
        Language[] values = Language.values();
        int currentIndex = settings.language.getLanguage().ordinal();
        int nextIndex = Math.floorMod(currentIndex + direction, values.length);
        settings.language.setLanguage(values[nextIndex]);
        settings.persist();
        languageLabel.setText(languageText(settings));
        languageLabel.update();
        snapRightOf(languageLabel, languageNext);
    }

    /**
     * Positions `button` just past `label`'s actual rendered right edge, not a guessed fixed
     * width — a label whose text length varies (language name, a rebound key's name, a
     * temporary capture/conflict message) must never run its text under a fixed-position
     * button. Call after every `label.update()` whose text just changed, not only at initial
     * layout. Y is untouched — a row's buttons never move vertically after layout.
     */
    private void snapRightOf(Text label, Button button) {
        button.getBounds().setX(label.getBounds().getX() + label.getWidth() + VALUE_GAP);
    }

    private static String languageText(SettingsManager settings) {
        return "Language: " + settings.language.getLanguage().name();
    }

    /** One volume row — label, current value, and +/- steppers; reused for Master/Music/SFX. */
    private class VolumeRow {
        private final Text label;
        private final java.util.function.IntSupplier getter;
        private final java.util.function.IntConsumer setter;
        private final Button decrement;
        private final Button increment;

        VolumeRow(String name, java.util.function.IntSupplier getter, java.util.function.IntConsumer setter) {
            this.getter = getter;
            this.setter = setter;
            this.label = new Text(text(name), FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.BODY_MEDIUM);
            addContent(label);
            this.decrement = stepButton("-", () -> change(name, -1));
            this.increment = stepButton("+", () -> change(name, 1));
        }

        private void change(String name, int delta) {
            int clamped = Math.max(1, Math.min(10, getter.getAsInt() + delta));
            setter.accept(clamped);
            label.setText(text(name));
            label.update();
            snapRightOf(label, increment);
        }

        private String text(String name) {
            return name + ": " + getter.getAsInt();
        }
    }

    /**
     * One rebindable keybind row (FEAT-015/A3) — label showing the function's current key, and
     * a Rebind button that starts listening for the next physical key press. While THIS row is
     * listening, clicking its own button again cancels; clicking the OTHER row's button is a
     * no-op (only one capture can be in flight at a time — InputManager tracks a single
     * capturingFunction) rather than silently stealing it and leaving this row's label stuck.
     */
    private class KeybindRow {
        private final InputFunction function;
        private final String displayName;
        private final Text label;
        private final Button rebindButton;

        KeybindRow(String displayName, InputFunction function) {
            this.function = function;
            this.displayName = displayName;
            this.label = new Text(idleText(), FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.BODY_MEDIUM);
            addContent(label);
            this.rebindButton = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Rebind", FontType.SILKSCREEN,
                    FontSize.BUTTON_DEFAULT.getSize(), 0, 0, KEYBIND_BUTTON_WIDTH, KEYBIND_BUTTON_HEIGHT, 0)
                .withOnClick((e) -> onRebindClicked(), ClickableEffectData.getImmediate())
                .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
                .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
                .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
            addContent(rebindButton);
        }

        private void onRebindClicked() {
            InputManager inputManager = GameContext.get().getInputManager();
            if (inputManager.isCapturing()) {
                if (inputManager.getCapturingFunction() == function) {
                    inputManager.cancelCapture();
                    show(idleText());
                }
                return; // a different row is listening — ignore this click rather than steal it
            }
            inputManager.startCapture(function, this::onCaptured);
            show("Press any key...");
        }

        private void onCaptured(RebindResult result) {
            show(result == RebindResult.OK ? idleText() : idleText() + " (in use)");
        }

        private void show(String text) {
            label.setText(text);
            label.update();
            snapRightOf(label, rebindButton);
        }

        private String idleText() {
            int keycode = GameContext.get().getSettingsManager().keybinds.getKeycode(function);
            return displayName + ": " + Input.Keys.toString(keycode);
        }
    }

    private void layoutContents() {
        int[] screenCenter = GameContext.get().getSettingsManager().screenSize.getScreenCenter();
        int screenCenterX = screenCenter[0];
        int screenCenterY = screenCenter[1];

        MenuLayout.centerHeader(header, 100);

        int rowTop = screenCenterY + ROW_SPACING;
        layoutVolumeRow(masterRow, screenCenterX, rowTop);
        layoutVolumeRow(musicRow, screenCenterX, rowTop - ROW_SPACING);
        layoutVolumeRow(sfxRow, screenCenterX, rowTop - ROW_SPACING * 2);

        int languageY = rowTop - ROW_SPACING * 3;
        languageLabel.update();
        languageLabel.getBounds().setX(screenCenterX - LABEL_WIDTH / 2);
        languageLabel.getBounds().setY(languageY);
        languagePrev.getBounds().setX(screenCenterX - LABEL_WIDTH / 2 - VALUE_GAP - STEP_BUTTON_SIZE);
        languagePrev.getBounds().setY(languageY - 10);
        languageNext.getBounds().setY(languageY - 10);
        snapRightOf(languageLabel, languageNext);

        layoutKeybindRow(pauseMenuRow, screenCenterX, rowTop - ROW_SPACING * 4);
        layoutKeybindRow(confirmSelectionRow, screenCenterX, rowTop - ROW_SPACING * 5);

        toggleFullscreen.getBounds().setX(screenCenterX - toggleFullscreen.getBounds().getWidth() / 2);
        toggleFullscreen.getBounds().setY(rowTop - ROW_SPACING * 6 - 10);

        backButton.getBounds().setX(screenCenterX - backButton.getBounds().getWidth() / 2);
        backButton.getBounds().setY(rowTop - ROW_SPACING * 7 - 20);
    }

    private void layoutVolumeRow(VolumeRow row, int centerX, int y) {
        row.label.update();
        row.label.getBounds().setX(centerX - LABEL_WIDTH / 2);
        row.label.getBounds().setY(y);
        row.decrement.getBounds().setX(centerX - LABEL_WIDTH / 2 - VALUE_GAP - STEP_BUTTON_SIZE);
        row.decrement.getBounds().setY(y - 10);
        row.increment.getBounds().setY(y - 10);
        snapRightOf(row.label, row.increment);
    }

    private void layoutKeybindRow(KeybindRow row, int centerX, int y) {
        row.label.update();
        row.label.getBounds().setX(centerX - LABEL_WIDTH / 2);
        row.label.getBounds().setY(y);
        row.rebindButton.getBounds().setY(y - 10);
        snapRightOf(row.label, row.rebindButton);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static SettingsRoom get() {
        return new SettingsRoom();
    }
}
