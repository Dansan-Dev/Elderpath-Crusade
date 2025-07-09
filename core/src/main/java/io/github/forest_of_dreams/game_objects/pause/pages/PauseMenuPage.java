package io.github.forest_of_dreams.game_objects.pause.pages;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import io.github.forest_of_dreams.data_objects.Text;
import io.github.forest_of_dreams.data_objects.TextList;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.enums.settings.PauseScreenPage;
import io.github.forest_of_dreams.game_objects.pause.PauseScreen;
import io.github.forest_of_dreams.managers.GraphicsManager;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.supers.HigherOrderTexture;

public class PauseMenuPage extends HigherOrderTexture {

    public PauseMenuPage() {
        addHeader();
        addOptions();
    }

    private void addHeader() {
        Text header = new Text(
            "PAUSED",
            FontType.WINDOW,
            0,
            SettingsManager.screenSize.getCurrentSize()[1] - 200,
            0,
            Color.WHITE
        );

        int screenCenterX = SettingsManager.screenSize.getScreenCenter()[0];
        int screenHeight = SettingsManager.screenSize.getCurrentSize()[1];

        header.getBounds().setX((int)(screenCenterX - (header.getLabel().getWidth() / 2)));
        header.getBounds().setY(screenHeight - 100);
        header.update();

        getRenderables().add(header);
    }

    private void addOptions() {
        int screenCenterX = SettingsManager.screenSize.getScreenCenter()[0];
        int screenCenterY = SettingsManager.screenSize.getScreenCenter()[1];

        TextList options = new TextList();
        options.addText(
            new Text("Resume", FontType.DEFAULT, 0, 0, 0, Color.WHITE)
                .withHoverColor(Color.YELLOW)
                .withClickColor(Color.BLUE)
                .withOnClick(GraphicsManager::unpause)
        );

        options.addText(
            new Text("Settings", FontType.DEFAULT, 0, 0, 0, Color.WHITE)
                .withHoverColor(Color.YELLOW)
                .withClickColor(Color.BLUE)
                .withOnClick(() -> PauseScreen.setCurrentPage(PauseScreenPage.SETTINGS))
        );

        options.addText(
            new Text("Exit", FontType.DEFAULT, 0, 0, 0, Color.WHITE)
                .withHoverColor(Color.YELLOW)
                .withClickColor(Color.BLUE)
                .withOnClick(() -> Gdx.app.exit())
        );

        options.alignTextAcrossYAxis(50, screenCenterX, screenCenterY);


        getRenderables().add(options);
    }
}
