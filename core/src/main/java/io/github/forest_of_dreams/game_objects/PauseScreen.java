package io.github.forest_of_dreams.game_objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.data_objects.Text;
import io.github.forest_of_dreams.data_objects.TextList;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.supers.AbstractTexture;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class PauseScreen extends AbstractTexture implements Renderable {
    private final Color backgroundColor = new Color(0, 0, 0, 0.6f);
    private final Color textColor = new Color(1, 1, 1, 1);
    private final TextureObject background;
    private final LabelStyle headerStyle;
    private final LabelStyle listStyle;
    private final Text header;
    private final TextList options = new TextList();

    public PauseScreen() {
        background = new TextureObject(backgroundColor, 0, 0, 100, 100);
        background.setColor(backgroundColor);
        background.setZ(10);

        int screenCenterX = SettingsManager.screenSize.getScreenCenter()[0];
        int screenCenterY = SettingsManager.screenSize.getScreenCenter()[1];
        int screenHeight = SettingsManager.screenSize.getCurrentSize()[1];

        this.header = new Text(
            "PAUSED",
            FontType.WINDOW,
            0,
            screenHeight - 200,
            0,
            Color.WHITE
        );
        header.getBounds().setX((int)(screenCenterX - (header.getLabel().getWidth() / 2)));
        header.getBounds().setY(screenHeight - 100);
        header.update();

        Skin skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
        headerStyle = skin.get("window", LabelStyle.class);
        listStyle = skin.get("default", LabelStyle.class);

        for (String text : List.of("Resume", "Settings", "Exit")) {
            options.addText(
                new Text(text, FontType.DEFAULT, 0, 0, 0, Color.WHITE)
                    .withHoverColor(Color.YELLOW)
                    .withClickColor(Color.BLUE)
            );
        }
        options.alignTextAcrossYAxis(50, screenCenterX, screenCenterY);
    }

    @Override
    public List<Integer> getZs() {
        return List.of();
    }

    public void renderPauseUI(SpriteBatch batch) {
        header.render(batch, 0, false);
        options.getRenderables().forEach(l -> l.render(batch, 0, false));
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        int[] screenSize = SettingsManager.screenSize.getCurrentSize();
        background.setBounds(new Box(0, 0, screenSize[0], screenSize[1]));
        background.render(batch, zLevel, isPaused);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {}
}
