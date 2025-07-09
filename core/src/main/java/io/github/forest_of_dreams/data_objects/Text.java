package io.github.forest_of_dreams.data_objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
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

import java.util.List;

public class Text extends AbstractTexture implements Renderable{
    @Getter @Setter
    private String text;
    @Getter @Setter
    private FontType fontType;
    private int z;

    private static final Skin skin = new Skin(Gdx.files.internal("ui/uiskin.json"));;
    private LabelStyle style;
    @Getter
    private Label label;

    private Color color;
    private Color hoverColor = null;
    private Color clickColor = null;

    public Text(String text, FontType fontType, int x, int y, int z, Color color) {
        this.text = text;
        this.fontType = fontType;
        this.z = z;
        this.color = color;

        setBounds(new Box(x, y, 0, 0));
        update();
    }

    public Text withHoverColor(Color hoverColor) {
        this.hoverColor = hoverColor;
        return this;
    }

    public Text withClickColor(Color clickColor) {
        this.clickColor = clickColor;
        return this;
    }

    public void update() {
        style = skin.get(fontType.getFontName(), Label.LabelStyle.class);
        label = new Label(text, style);
        Box bounds = getBounds();
        label.setPosition(bounds.getX(), bounds.getY());
        label.setColor(color);
        getBounds().setWidth((int)label.getWidth());
        getBounds().setHeight((int)label.getHeight());
    }

    public void setCenterX() {
        getBounds().setX((int)(SettingsManager.screenSize.getScreenCenter()[0] - (label.getWidth() / 2)));
    }

    public void setCenterY() {
        getBounds().setY((int)(SettingsManager.screenSize.getScreenCenter()[1] - (label.getHeight() / 2)));
    }

    private boolean isHovered(int relX, int relY) {
        int x = getX() + relX;
        int y = getY() + relY;
        int width = getWidth();
        int height = getHeight();
        float mouseX = Gdx.input.getX();
        float mouseY = Gdx.graphics.getHeight() - Gdx.input.getY();

        float scaleX = (float)Gdx.graphics.getWidth() / SettingsManager.screenSize.getCurrentSize()[0];
        float scaleY = (float)Gdx.graphics.getHeight() / SettingsManager.screenSize.getCurrentSize()[1];
        mouseX /= scaleX;
        mouseY /= scaleY;
        mouseX = (float) Math.floor(mouseX);
        mouseY = (float) Math.floor(mouseY);

        boolean isInXRange = x <= mouseX && mouseX <= (x + width - 1);
        boolean isInYRange = y <= mouseY && mouseY <= (y + height - 1);

        return isInXRange && isInYRange;
    }

    private boolean isClicked() {
        return Gdx.input.isButtonPressed(0);
    }

    @Override
    public List<Integer> getZs() {
        return List.of();
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        if (zLevel != z) return;
        if (isPaused) return;
        update();
        if (isHovered(0, 0)) {
            if (hoverColor != null) label.setColor(hoverColor);
            if (isClicked() && clickColor != null) label.setColor(clickColor);
        }
        label.draw(batch, 1);
        label.setColor(color);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        if (zLevel != z) return;
        if (isPaused) return;
        label.setPosition(x, y);
        if (isHovered(x, y)) {
            if (hoverColor != null) label.setColor(hoverColor);
            if (isClicked() && clickColor != null) label.setColor(hoverColor);
        }
        label.draw(batch, 1);
        label.setColor(color);
    }
}
