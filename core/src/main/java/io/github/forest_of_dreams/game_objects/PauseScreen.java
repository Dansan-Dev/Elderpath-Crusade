package io.github.forest_of_dreams.game_objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.data_objects.Text;
import io.github.forest_of_dreams.data_objects.TextList;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.managers.GraphicsManager;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.supers.AbstractTexture;

public class PauseScreen extends AbstractTexture {
    private static final TextureObject background = new TextureObject(
        new Color(0, 0, 0, 0.6f),
        0, 0, 100, 100);

    private static final Text header = new Text(
        "PAUSED",
        FontType.WINDOW,
        0,
        SettingsManager.screenSize.getCurrentSize()[1] - 200,
        0,
        Color.WHITE
    );
    private static final TextList options = new TextList();

    public static void initialize() {
        background.setZ(10);

        int screenCenterX = SettingsManager.screenSize.getScreenCenter()[0];
        int screenCenterY = SettingsManager.screenSize.getScreenCenter()[1];

        header.getBounds().setX((int)(screenCenterX - (header.getLabel().getWidth() / 2)));
        header.getBounds().setY(SettingsManager.screenSize.getCurrentSize()[1] - 100);
        header.update();

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
        );

        options.addText(
            new Text("Exit", FontType.DEFAULT, 0, 0, 0, Color.WHITE)
                .withHoverColor(Color.YELLOW)
                .withClickColor(Color.BLUE)
                .withOnClick(() -> Gdx.app.exit())
        );

        options.alignTextAcrossYAxis(50, screenCenterX, screenCenterY);
    }

    public static void renderPauseUI(SpriteBatch batch) {
        header.render(batch, 0, false);
        options.getRenderables().forEach(l -> l.render(batch, 0, false));
    }

    public static void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        int[] screenSize = SettingsManager.screenSize.getCurrentSize();
        background.setBounds(new Box(0, 0, screenSize[0], screenSize[1]));
        background.render(batch, zLevel, isPaused);
    }
}
