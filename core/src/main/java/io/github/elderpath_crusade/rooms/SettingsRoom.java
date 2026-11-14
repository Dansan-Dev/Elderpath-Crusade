package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.enums.settings.Language;
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
 */
public class SettingsRoom extends Room {
    private static final int ROW_SPACING = 70;
    private static final int STEP_BUTTON_SIZE = 50;
    private static final int LABEL_WIDTH = 260;
    private static final int VALUE_GAP = 20;

    private Text header;
    private VolumeRow masterRow;
    private VolumeRow musicRow;
    private VolumeRow sfxRow;
    private Text languageLabel;
    private Button languagePrev;
    private Button languageNext;
    private Button toggleFullscreen;
    private Button backButton;

    private SettingsRoom() {
        super();
        SettingsManager settings = GameContext.get().getSettingsManager();

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
            .withOnClick((e) -> GameContext.get().getRoomManager().gotoRoom(MainMenuRoom::get), ClickableEffectData.getImmediate())
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
        }

        private String text(String name) {
            return name + ": " + getter.getAsInt();
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
        languageNext.getBounds().setX(screenCenterX + LABEL_WIDTH / 2 + VALUE_GAP);
        languageNext.getBounds().setY(languageY - 10);

        toggleFullscreen.getBounds().setX(screenCenterX - toggleFullscreen.getBounds().getWidth() / 2);
        toggleFullscreen.getBounds().setY(rowTop - ROW_SPACING * 4 - 10);

        backButton.getBounds().setX(screenCenterX - backButton.getBounds().getWidth() / 2);
        backButton.getBounds().setY(rowTop - ROW_SPACING * 5 - 20);
    }

    private void layoutVolumeRow(VolumeRow row, int centerX, int y) {
        row.label.update();
        row.label.getBounds().setX(centerX - LABEL_WIDTH / 2);
        row.label.getBounds().setY(y);
        row.decrement.getBounds().setX(centerX - LABEL_WIDTH / 2 - VALUE_GAP - STEP_BUTTON_SIZE);
        row.decrement.getBounds().setY(y - 10);
        row.increment.getBounds().setX(centerX + LABEL_WIDTH / 2 + VALUE_GAP);
        row.increment.getBounds().setY(y - 10);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static SettingsRoom get() {
        return new SettingsRoom();
    }
}
