package io.github.forest_of_dreams.characters.sprites.checker_sprites.__super__;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.path_loaders.ImagePathSpritesAndAnimations;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.data_objects.Text;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.game_objects.CharacterSprite;

public abstract class CheckerSprite extends CharacterSprite {
    private Text text;

    public enum AlignmentColor {
        RED, BLUE;
    }

    private void initializeImage(AlignmentColor color) {
        String path = switch (color) {
            case RED -> ImagePathSpritesAndAnimations.RED_CHECKER.getPath();
            case BLUE -> ImagePathSpritesAndAnimations.BLUE_CHECKER.getPath();
        };
        int[] size = {468, 479};
        makeAnimationOfSpriteSheetRow(
            "passive",
            path,
            1,
            0, 0,
            size[0], size[1],
            0
        );
    }

    public CheckerSprite(int x, int y, int width, int height, String name, AlignmentColor color) {
        super(x, y, width, height);
        initializeImage(color);
        text = new Text(name, FontType.DEFAULT, 0, 0, 2, Color.WHITE);
        Box newBounds = new Box(
            width/2 - text.getWidth()/2,
            height/2 - text.getHeight()/2,
            width, height
        );
        text.setBounds(newBounds);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        super.render(batch, zLevel, isPaused);
        if (zLevel == 2) {
            text.render(batch, zLevel, isPaused);
        }
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        super.render(batch, zLevel, isPaused, x, y);
        if (zLevel == 2) {
            text.render(batch, zLevel, isPaused, x + text.getX(), y + text.getY());
        }
    }
}
