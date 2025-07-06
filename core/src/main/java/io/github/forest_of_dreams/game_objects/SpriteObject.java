package io.github.forest_of_dreams.game_objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.enums.SpriteBoxPos;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.supers.AbstractTexture;

import java.util.List;

public class SpriteObject extends AbstractTexture implements Renderable {
    private Sprite sprite = null;
    protected int z;
    private final SpriteBoxPos spriteBoxPos;

    public SpriteObject(int x, int y, int width, int height, int z, SpriteBoxPos spriteBoxPos) {
        setBounds(new Box(x, y, width, height));
        this.z = z;
        this.spriteBoxPos = spriteBoxPos;
    }

    public void setSprite(String path, int sheetX, int sheetY, int width, int height, int newWidth, int newHeight) {
        Texture texture = new Texture(Gdx.files.internal(path));
        this.sprite = new Sprite(texture, sheetX, sheetY, width, height);
        this.sprite.setSize(newWidth, newHeight);
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

    private int[] calculateMargin() {
        int[] vector = switch (spriteBoxPos) {
            case TOP_LEFT -> new int[]{0, 2};
            case TOP -> new int[]{1, 2};
            case TOP_RIGHT -> new int[]{2, 2};
            case BOTTOM_LEFT -> new int[]{0, 0};
            case BOTTOM -> new int[]{1, 0};
            case BOTTOM_RIGHT -> new int[]{2, 0};
            case LEFT -> new int[]{0, 1};
            case CENTER -> new int[]{1, 1};
            case RIGHT -> new int[]{2, 1};
        };
        Box bounds = getBounds();
        int marginWidthSize = (int)(bounds.getWidth() - sprite.getWidth()) / 2;
        int marginHeightSize = bounds.getHeight() / 2;
        return new int[]{marginWidthSize * vector[0], marginHeightSize * vector[1]};
    }

    @Override
    public List<Integer> getZs() {
        return List.of(z);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel) {
        if (zLevel != z) return;
        int[] margin = calculateMargin();
        batch.draw(
            sprite,
            getX() + margin[0],
            getY() + margin[1],
            sprite.getWidth(),
            sprite.getHeight()
        );
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, int x, int y) {
        if (zLevel != z) return;
        int[] margin = calculateMargin();
        batch.draw(
            sprite,
            x + getX() + margin[0],
            y + getY() + margin[1],
            sprite.getWidth(),
            sprite.getHeight()
        );
    }
}
