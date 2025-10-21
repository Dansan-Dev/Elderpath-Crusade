package io.github.forest_of_dreams.game_objects.pause.pages;

import com.badlogic.gdx.Gdx;
import io.github.forest_of_dreams.utils.ColorSettings;
import io.github.forest_of_dreams.data_objects.ClickableEffectData;
import io.github.forest_of_dreams.ui_objects.Text;
import io.github.forest_of_dreams.ui_objects.TextList;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.enums.settings.PauseScreenPage;
import io.github.forest_of_dreams.game_objects.pause.PauseScreen;
import io.github.forest_of_dreams.managers.GameManager;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.managers.Game;
import io.github.forest_of_dreams.rooms.MainMenuRoom;
import io.github.forest_of_dreams.supers.HigherOrderTexture;
import io.github.forest_of_dreams.utils.MenuLayout;

public class PauseMenuPage extends HigherOrderTexture {
    public static PauseMenuPage get() {
        return new PauseMenuPage();
    }

    private final Text header = new Text(
        "PAUSED",
        FontType.SILKSCREEN,
        0,
        SettingsManager.screenSize.getScreenHeight() - 200,
        0,
        ColorSettings.TEXT_DEFAULT.getColor()
    ).withFontSize(io.github.forest_of_dreams.utils.FontSize.TITLE_LARGE).asPauseUI();
    private final TextList options = new TextList();

    public PauseMenuPage() {
        getRenderables().add(header);
        addOptions();
        update();
    }

    private void addOptions() {
        options.addText(
            new Text("Resume", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(io.github.forest_of_dreams.utils.FontSize.BODY_MEDIUM)
                .withHoverColor(ColorSettings.TEXT_HOVER.getColor())
                .withClickColor(ColorSettings.TEXT_CLICK.getColor())
                .withOnClick((e) -> GameManager.unpause(), ClickableEffectData.getImmediate())
                .asPauseUI()
        );

        options.addText(
            new Text("Settings", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(io.github.forest_of_dreams.utils.FontSize.BODY_MEDIUM)
                .withHoverColor(ColorSettings.TEXT_HOVER.getColor())
                .withClickColor(ColorSettings.TEXT_CLICK.getColor())
                .withOnClick((e) -> PauseScreen.setCurrentPage(PauseScreenPage.SETTINGS), ClickableEffectData.getImmediate())
                .asPauseUI()
        );

        options.addText(
            new Text("Main Menu", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(io.github.forest_of_dreams.utils.FontSize.BODY_MEDIUM)
                .withHoverColor(ColorSettings.TEXT_HOVER.getColor())
                .withClickColor(ColorSettings.TEXT_CLICK.getColor())
                .withOnClick(
                    (e) -> {
                        Game.gotoRoom(MainMenuRoom::get);
                        GameManager.unpause();
                    },
                    ClickableEffectData.getImmediate()
                )
                .asPauseUI()
        );

        options.addText(
            new Text("Exit", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(io.github.forest_of_dreams.utils.FontSize.BODY_MEDIUM)
                .withHoverColor(ColorSettings.TEXT_HOVER.getColor())
                .withClickColor(ColorSettings.TEXT_CLICK.getColor())
                .withOnClick((e) -> Gdx.app.exit(), ClickableEffectData.getImmediate())
                .asPauseUI()
        );

        getRenderables().add(options);
    }

    public void update() {
        // Centralized layout for header and options
        MenuLayout.layoutHeaderAndOptions(header, options, 50, 100);
    }
}
