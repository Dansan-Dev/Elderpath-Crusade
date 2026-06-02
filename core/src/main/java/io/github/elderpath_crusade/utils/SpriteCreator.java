package io.github.elderpath_crusade.utils;

import io.github.elderpath_crusade.GameContext;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;

/**
 * Utility class for creating sprites.
 */
public class SpriteCreator {
    /**
     *
     * @param path the path to the image (ex. "images/image.png")
     * @param sheetX the x coordinate of the sprite in the sheet
     * @param sheetY the y coordinate of the sprite in the sheet
     * @param width the width of the sprite in the sheet
     * @param height the height of the sprite in the sheet
     * @param newWidth the width of the sprite after scaling
     * @param newHeight the height of the sprite after scaling
     * @return
     */
    public static Sprite makeSprite(String path, int sheetX, int sheetY, int width, int height, int newWidth, int newHeight) {
        Texture texture = GameContext.get().getTextureManager().getTexture(path);
        if (texture == null) return null;
        Sprite sprite = new Sprite(texture, sheetX, sheetY, width, height);
        sprite.setSize(newWidth, newHeight);
        return sprite;
    }

    /**
     * Atlas-aware sprite creation. Tries atlas region first, falls back to file.
     *
     * @param regionName atlas region name (e.g. "gobu_walk")
     * @param newWidth the width of the sprite after scaling
     * @param newHeight the height of the sprite after scaling
     * @return
     */
    public static Sprite makeSprite(String regionName, int newWidth, int newHeight) {
        TextureAtlas.AtlasRegion region = GameContext.get().getTextureManager().getAtlasRegion(regionName);
        if (region != null) {
            Sprite sprite = new Sprite(region);
            sprite.setSize(newWidth, newHeight);
            return sprite;
        }
        return makeSprite("images/" + regionName + ".png", 0, 0, newWidth, newHeight, newWidth, newHeight);
    }

    /**
     * Atlas-aware sprite creation with sub-region coordinates (for sprite sheets packed as single atlas regions).
     *
     * @param regionName atlas region name
     * @param sheetX x offset within the region
     * @param sheetY y offset within the region
     * @param width frame width within the region
     * @param height frame height within the region
     * @param newWidth the width of the sprite after scaling
     * @param newHeight the height of the sprite after scaling
     * @return
     */
    public static Sprite makeSpriteFromRegion(String regionName, int sheetX, int sheetY, int width, int height, int newWidth, int newHeight) {
        TextureAtlas.AtlasRegion region = GameContext.get().getTextureManager().getAtlasRegion(regionName);
        if (region != null) {
            Sprite sprite = new Sprite(region.getTexture(), region.getRegionX() + sheetX, region.getRegionY() + sheetY, width, height);
            sprite.setSize(newWidth, newHeight);
            return sprite;
        }
        return makeSprite("images/" + regionName + ".png", sheetX, sheetY, width, height, newWidth, newHeight);
    }
}
