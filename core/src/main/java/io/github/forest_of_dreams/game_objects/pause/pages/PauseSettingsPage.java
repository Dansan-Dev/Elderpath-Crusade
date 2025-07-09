package io.github.forest_of_dreams.game_objects.pause.pages;

import com.badlogic.gdx.graphics.Color;
import io.github.forest_of_dreams.data_objects.Text;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.supers.HigherOrderTexture;

public class PauseSettingsPage extends HigherOrderTexture {
    public PauseSettingsPage() {
        addHeader();
    }

    private void addHeader() {
        Text header = new Text(
            "SETTINGS",
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
}
