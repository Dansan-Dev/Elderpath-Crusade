package io.github.forest_of_dreams.game_objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.supers.BaseTexture;
import io.github.forest_of_dreams.utils.GraphicUtils;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class TextureObject extends BaseTexture {
    private Color color;
    private Color hoverColor = null;


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

    public boolean isHovered() {
        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();
        float mouseX = Gdx.input.getX();
        float mouseY = Gdx.graphics.getHeight() - Gdx.input.getY();
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public void render(SpriteBatch batch, int zLevel) {
        Box bounds = getBounds();
        int[] renderPos = calculatePos();
        if (zLevel != z) return;
        Color renderedColor = (hoverColor != null && isHovered()) ?
            hoverColor : this.color;

        batch.draw(
            GraphicUtils.getPixelTexture(renderedColor),
            renderPos[0],
            renderPos[1],
            bounds.getWidth(),
            bounds.getHeight()
        );
    }
}
