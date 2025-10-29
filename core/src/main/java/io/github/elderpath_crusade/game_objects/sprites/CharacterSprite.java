package io.github.elderpath_crusade.game_objects.sprites;

import com.badlogic.gdx.graphics.g2d.Sprite;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.enums.SpriteBoxPos;
import io.github.elderpath_crusade.supers.HigherOrderTexture;
import io.github.elderpath_crusade.utils.SpriteCreator;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Character sprite object
 * Contains a SpriteObject
 * Sets up animations from sprite sheets
 */
public class CharacterSprite extends HigherOrderTexture {
    @Getter private SpriteObject sprite;

    public CharacterSprite(int x, int y, int width, int height) {
        setBounds(new Box(x, y, width, height));

        sprite = new SpriteObject(x, y, width, height, 2, SpriteBoxPos.BOTTOM);
        setRenderables(List.of(sprite));
    }

    public CharacterSprite() {
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
