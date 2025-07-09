package io.github.forest_of_dreams.data_objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.supers.AbstractTexture;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

public class Text extends AbstractTexture implements Renderable{
    @Getter @Setter
    private String text;
    @Getter @Setter
    private int x;
    @Getter @Setter
    private int y;
    private int z;
    @Getter @Setter
    private FontType fontType;
    private static final Skin skin = new Skin(Gdx.files.internal("ui/uiskin.json"));;
    private LabelStyle style;
    @Getter
    private Label label;

    public Text(String text, FontType fontType, int x, int y, int z) {
        this.text = text;
        this.fontType = fontType;
        this.x = x;
        this.y = y;
        this.z = z;


        update();
    }

    public void update() {
        style = skin.get(fontType.getFontName(), Label.LabelStyle.class);
        label = new Label(text, style);
        label.setPosition(x, y);
    }

    public void setCenterX() {
        setX((int)(SettingsManager.screenSize.getScreenCenter()[0] - (label.getWidth() / 2)));
    }

    public void setCenterY() {
        setY((int)(SettingsManager.screenSize.getScreenCenter()[1] - (label.getHeight() / 2)));
    }

    @Override
    public List<Integer> getZs() {
        return List.of();
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        if (zLevel != z) return;
        if (isPaused) return;
        label.draw(batch, 1);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        if (zLevel != z) return;
        if (isPaused) return;
        label.setPosition(x, y);
        label.draw(batch, 1);
    }
}
