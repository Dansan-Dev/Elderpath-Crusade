package io.github.forest_of_dreams.game_objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.utils.GraphicUtils;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class TextureObject implements Renderable {
    private Color color;
    private Color hoverColor = null;
    private int x;
    private int y;
    private int z = 0;
    private int width;
    private int height;

    public TextureObject(Color color, int x, int y, int width, int height) {
        this.color = color;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public TextureObject(Color color, int x, int y, int width, int height, int z) {
        this.color = color;
        this.x = x;
        this.y = y;
        this.z = z;
        this.width = width;
        this.height = height;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public boolean isHovered() {
        float mouseX = Gdx.input.getX();
        float mouseY = Gdx.graphics.getHeight() - Gdx.input.getY();
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public void render(SpriteBatch batch) {
        Color renderedColor = (hoverColor != null && isHovered()) ?
            hoverColor : this.color;
        batch.draw(GraphicUtils.getPixelTexture(renderedColor), x, y, width, height);
    }
}
