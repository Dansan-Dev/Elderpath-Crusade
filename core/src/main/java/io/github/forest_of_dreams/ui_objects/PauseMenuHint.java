package io.github.forest_of_dreams.ui_objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.data_objects.Text;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.interfaces.UIRenderable;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.supers.AbstractTexture;

import java.util.List;

public class PauseMenuHint extends AbstractTexture implements UIRenderable {
//    private final LabelStyle style;
//    private final Label text;
    public final Text text;

    public PauseMenuHint(int x, int y) {
        // Using Silkscreen font; FontManager normalizes glyph RGB to white at load time
        // so tinting works correctly.
        text = new Text(
            "ESC",
            FontType.SILKSCREEN,
            x, y,
            0,
            Color.WHITE
        ).withFontSize(12);
//        Skin skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
//        style = skin.get("window", LabelStyle.class);
//        text = new Label("ESC", style);
//        text.setPosition(20, SettingsManager.screenSize.getScreenHeight() - 30);
//        text.setColor(Color.WHITE);
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused) {
        if (isPaused) return;
        text.render(batch, 0, false);
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused, int x, int y) {
        if (isPaused) return;
        text.setBounds(new Box(x, y, text.getWidth(), text.getHeight()));
        text.render(batch, 0, false);
    }
}
