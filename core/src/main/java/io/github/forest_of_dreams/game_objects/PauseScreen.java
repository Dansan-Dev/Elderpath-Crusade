package io.github.forest_of_dreams.game_objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import io.github.forest_of_dreams.data_objects.Box;
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
    private final Label header;
    private final List<Label> options;

    public PauseScreen() {
        background = new TextureObject(backgroundColor, 0, 0, 100, 100);
        background.setColor(backgroundColor);
        background.setZ(10);

        Skin skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
        headerStyle = skin.get("window", LabelStyle.class);
        listStyle = skin.get("default", LabelStyle.class);

        int screenCenterX = SettingsManager.screenSize.getScreenCenter()[0];
        int screenCenterY = SettingsManager.screenSize.getScreenCenter()[1];
        int screenHeight = SettingsManager.screenSize.getCurrentSize()[1];

        header = new Label("PAUSED", headerStyle);
        header.setPosition(screenCenterX - (header.getWidth() / 2), screenHeight - 100);

        options = new ArrayList<>();
        options.add(new Label("Resume", listStyle));
        options.add(new Label("Settings", listStyle));
        options.add(new Label("Exit", listStyle));

        IntStream.range(0, options.size()).
            forEach(i -> {
                Label label = options.get(i);
                label.setColor(textColor);
                float distance = 50;
                label.setPosition(
                    screenCenterX - (label.getWidth() / 2),
                    screenCenterY + (options.size()-1)*(distance/2) - i*distance
                );
            });

    }

    @Override
    public List<Integer> getZs() {
        return List.of();
    }

    public void renderPauseUI(SpriteBatch batch) {
        header.draw(batch, 1);
        options.forEach(l -> l.draw(batch, 1));
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
