package io.github.forest_of_dreams.game_objects.pause.pages;

import io.github.forest_of_dreams.utils.ColorSettings;
import io.github.forest_of_dreams.data_objects.ClickableEffectData;
import io.github.forest_of_dreams.ui_objects.Text;
import io.github.forest_of_dreams.ui_objects.TextList;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.enums.settings.PauseScreenPage;
import io.github.forest_of_dreams.game_objects.pause.PauseScreen;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.supers.HigherOrderTexture;
import io.github.forest_of_dreams.utils.MenuLayout;

public class PauseSettingsPage extends HigherOrderTexture {
    private final Text header = new Text(
        "SETTINGS",
        FontType.SILKSCREEN,
        0,
        SettingsManager.screenSize.getScreenHeight() - 200,
        0,
        ColorSettings.TEXT_DEFAULT.getColor()
    ).withFontSize(io.github.forest_of_dreams.utils.FontSize.TITLE_LARGE).asPauseUI();
    private final TextList options = new TextList();

    public PauseSettingsPage() {
        getRenderables().add(header);
        addOptions();
        layout();
    }

    private void addOptions() {
        options.addText(
            new Text("Toggle Fullscreen", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(io.github.forest_of_dreams.utils.FontSize.BODY_MEDIUM)
                .withHoverColor(ColorSettings.TEXT_HOVER.getColor())
                .withClickColor(ColorSettings.TEXT_CLICK.getColor())
                .withOnClick((e) -> SettingsManager.screenSize.toggleFullscreen(), ClickableEffectData.getImmediate())
                .asPauseUI()
        );

        options.addText(
            new Text("Back", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(io.github.forest_of_dreams.utils.FontSize.BODY_MEDIUM)
                .withHoverColor(ColorSettings.TEXT_HOVER.getColor())
                .withClickColor(ColorSettings.TEXT_CLICK.getColor())
                .withOnClick((e) -> PauseScreen.setCurrentPage(PauseScreenPage.MENU), ClickableEffectData.getImmediate())
                .asPauseUI()
        );

        getRenderables().add(options);
    }

    public void layout() {
        // Centralized layout for header and options
        MenuLayout.layoutHeaderAndOptions(header, options, 50, 100);
    }
}
