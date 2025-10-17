package io.github.forest_of_dreams.game_objects.pause.pages;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import io.github.forest_of_dreams.data_objects.ClickableEffectData;
import io.github.forest_of_dreams.data_objects.Text;
import io.github.forest_of_dreams.data_objects.TextList;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.enums.settings.PauseScreenPage;
import io.github.forest_of_dreams.game_objects.pause.PauseScreen;
import io.github.forest_of_dreams.managers.GameManager;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.managers.Game;
import io.github.forest_of_dreams.rooms.MainMenuRoom;
import io.github.forest_of_dreams.supers.HigherOrderTexture;

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
        Color.WHITE
    ).withFontSize(36).asPauseUI();
    private final TextList options = new TextList();

    public PauseMenuPage() {
        getRenderables().add(header);
        addOptions();
        update();
    }

    private void addOptions() {
        options.addText(
            new Text("Resume", FontType.SILKSCREEN, 0, 0, 0, Color.WHITE)
                .withFontSize(16)
                .withHoverColor(Color.YELLOW)
                .withClickColor(Color.BLUE)
                .withOnClick((e) -> GameManager.unpause(), ClickableEffectData.getImmediate())
                .asPauseUI()
        );

        options.addText(
            new Text("Settings", FontType.SILKSCREEN, 0, 0, 0, Color.WHITE)
                .withFontSize(16)
                .withHoverColor(Color.YELLOW)
                .withClickColor(Color.BLUE)
                .withOnClick((e) -> PauseScreen.setCurrentPage(PauseScreenPage.SETTINGS), ClickableEffectData.getImmediate())
                .asPauseUI()
        );

        options.addText(
            new Text("Main Menu", FontType.SILKSCREEN, 0, 0, 0, Color.WHITE)
                .withFontSize(16)
                .withHoverColor(Color.YELLOW)
                .withClickColor(Color.BLUE)
                .withOnClick(
                    (e) -> {
                        Game.gotoRoom(MainMenuRoom.get());
                        GameManager.unpause();
                    },
                    ClickableEffectData.getImmediate()
                )
                .asPauseUI()
        );

        options.addText(
            new Text("Exit", FontType.SILKSCREEN, 0, 0, 0, Color.WHITE)
                .withFontSize(16)
                .withHoverColor(Color.YELLOW)
                .withClickColor(Color.BLUE)
                .withOnClick((e) -> Gdx.app.exit(), ClickableEffectData.getImmediate())
                .asPauseUI()
        );

        getRenderables().add(options);
    }

    public void update() {
        int screenCenterX = SettingsManager.screenSize.getScreenCenter()[0];
        int screenCenterY = SettingsManager.screenSize.getScreenCenter()[1];
        int screenHeight = SettingsManager.screenSize.getScreenHeight();

        // Header
        header.getBounds().setX((int)(screenCenterX - (header.getLabel().getWidth() / 2)));
        header.getBounds().setY(screenHeight - 100);
        header.update();

        // Options
        options.alignTextAcrossYAxis(50, screenCenterX, screenCenterY);
    }
}
