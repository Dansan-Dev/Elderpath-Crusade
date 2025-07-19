package io.github.forest_of_dreams.game_objects;

import com.badlogic.gdx.graphics.g2d.Sprite;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.enums.SpriteBoxPos;
import io.github.forest_of_dreams.supers.HigherOrderTexture;
import io.github.forest_of_dreams.utils.SpriteCreator;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class CharacterSprite extends HigherOrderTexture {
    @Getter private SpriteObject sprite;

    public CharacterSprite(int x, int y, int width, int height) {
        setBounds(new Box(x, y, width, height));

        sprite = new SpriteObject(x, y, width, height, 1, SpriteBoxPos.BOTTOM);
        setRenderables(List.of(sprite));
    }

    protected void makeAnimationOfSpriteSheetRow(String name, String path, int spriteAmount, int startX, int startY, int spriteSheetCharacterWidth, int spriteSheetCharacterHeight, int updatesPerSecond) {
        List<Sprite> sprites = new ArrayList<>();
        Stream.iterate(startX, i -> i + spriteSheetCharacterWidth).limit(spriteAmount).forEach(i -> {
            sprites.add(
                SpriteCreator.makeSprite(
                    path,
                    i, startY,
                    spriteSheetCharacterWidth, spriteSheetCharacterHeight,
                    getWidth(), getHeight()
                )
            );
        });
        getSprite().addAnimation(name, sprites, updatesPerSecond);
    }
}
