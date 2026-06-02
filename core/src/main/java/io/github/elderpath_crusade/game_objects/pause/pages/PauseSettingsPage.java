package io.github.elderpath_crusade.game_objects.pause.pages;
import io.github.elderpath_crusade.GameContext;

import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.ui_objects.TextList;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.settings.PauseScreenPage;
import io.github.elderpath_crusade.game_objects.pause.PauseScreen;
import io.github.elderpath_crusade.config.SettingsManager;
import io.github.elderpath_crusade.supers.HigherOrderTexture;
import io.github.elderpath_crusade.utils.MenuLayout;

public class PauseSettingsPage extends HigherOrderTexture {
    private final Text header = new Text(
        "SETTINGS",
        FontType.SILKSCREEN,
        0,
        GameContext.get().getSettingsManager().screenSize.getScreenHeight() - 200,
        0,
        ColorSettings.TEXT_DEFAULT.getColor()
    ).withFontSize(io.github.elderpath_crusade.utils.FontSize.TITLE_LARGE).asPauseUI();
    private final TextList options = new TextList();

    public PauseSettingsPage() {
        getRenderables().add(header);
        addOptions();
        layout();
    }

    private void addOptions() {
        options.addText(
            new Text("Toggle Fullscreen", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(io.github.elderpath_crusade.utils.FontSize.BODY_MEDIUM)
                .withHoverColor(ColorSettings.TEXT_HOVER.getColor())
                .withClickColor(ColorSettings.TEXT_CLICK.getColor())
                .withOnClick((e) -> GameContext.get().getSettingsManager().screenSize.toggleFullscreen(), ClickableEffectData.getImmediate())
                .asPauseUI()
        );

        options.addText(
            new Text("Back", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(io.github.elderpath_crusade.utils.FontSize.BODY_MEDIUM)
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
