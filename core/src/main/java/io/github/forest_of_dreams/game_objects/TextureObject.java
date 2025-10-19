package io.github.forest_of_dreams.game_objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.supers.BaseTexture;
import io.github.forest_of_dreams.utils.GraphicUtils;
import io.github.forest_of_dreams.utils.HoverUtils;
import lombok.Getter;
import lombok.Setter;

/**
 * Simple texture object
 * It returns a square of a certain color
 */
@Getter @Setter
public class TextureObject extends BaseTexture {
    private Color color;
    private Color hoverColor = null;
    private Color clickColor = null;


    public TextureObject(Color color, int x, int y, int width, int height) {
        super(0);
        this.color = color;
        setBounds(new Box(x, y, width, height));
    }

    public TextureObject(Color color, int x, int y, int width, int height, int z) {
        super(z);
        this.color = color;
        setBounds(new Box(x, y, width, height));
    }

    public void setSize(int width, int height) {
        Box bounds = getBounds();
        bounds.setWidth(width);
        bounds.setHeight(height);
    }

    public void setPosition(int x, int y) {
        Box bounds = getBounds();
        bounds.setX(x);
        bounds.setY(y);
    }

    public boolean isHovered(int relX, int relY) {
        int x = getX() + relX;
        int y = getY() + relY;
        return HoverUtils.isHovered(x, y, getWidth(), getHeight());
    }

    private boolean isClicked() {
        return Gdx.input.isButtonPressed(0);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        if (zLevel != z) return;
        Box bounds = getBounds();
        int[] renderPos = calculatePos();

        Color renderedColor;
        if (isHovered(0, 0)) {
            if (isClicked() && clickColor != null) {
                renderedColor = clickColor;
            } else if (hoverColor != null) {
                renderedColor = hoverColor;
            } else {
                renderedColor = color;
            }
        } else renderedColor = color;

        batch.draw(
            GraphicUtils.getPixelTexture(renderedColor),
            renderPos[0],
            renderPos[1],
            bounds.getWidth(),
            bounds.getHeight()
        );
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        if (zLevel != z) return;
        Box bounds = getBounds();
        int[] renderBasePos = calculatePos();

        Color renderedColor;
        if (!isPaused && isHovered(x, y)) {
            if (Gdx.input.isButtonPressed(0) && clickColor != null) {
                renderedColor = clickColor;
            } else if (hoverColor != null) {
                renderedColor = hoverColor;
            } else {
                renderedColor = color;
            }
        } else renderedColor = color;

        batch.draw(
            GraphicUtils.getPixelTexture(renderedColor),
            x + renderBasePos[0],
            y + renderBasePos[1],
            bounds.getWidth(),
            bounds.getHeight()
        );
    }
}
