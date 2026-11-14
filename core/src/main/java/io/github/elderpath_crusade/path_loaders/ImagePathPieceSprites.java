package io.github.elderpath_crusade.path_loaders;

import io.github.elderpath_crusade.match.SyntheticCardNames;
import lombok.Getter;

/**
 * Per-piece figure art (ART-002), drawn on top of the side-colored checker by
 * {@code CheckerSprite}. One constant per {@code pieces.yaml} registry key; a piece with no
 * constant here keeps the plain checker with its name label.
 */
@Getter
public enum ImagePathPieceSprites {
    WOLF("Wolf"),
    WOLF_CUB("WolfCub"),
    BARBARIAN("Barbarian"),
    BIG_TOAD("BigToad"),
    CHARGER("Charger"),
    COMMANDER("Commander"),
    CROSSBOWMAN("Crossbowman"),
    CROW("Crow"),
    FAIRY("Fairy"),
    HERO("Hero"),
    KING("King"),
    RIFLEMAN("Rifleman"),
    ROGUE("Rogue"),
    SHOCKLING("Shockling"),
    SKELETON_BOMBER("SkeletonBomber"),
    SNIPER("Sniper"),
    STORM_MAGE("StormMage"),
    WARP_MAGE("WarpMage"),
    WIND_SPIRIT("WindSpirit");

    /** Native size of every file in {@code images/pieces/} — the crop the sprite code uses. */
    public static final int SIZE = 64;

    private final String registryKey;
    private final String path;

    ImagePathPieceSprites(String registryKey) {
        this.registryKey = registryKey;
        this.path = "images/pieces/" + registryKey + ".png";
    }

    /**
     * The figure art for a piece's registry key, or {@code null} if it has none. A run-modified
     * piece's synthetic key (e.g. {@code "Crow#run-6"}) uses its base piece's art.
     */
    public static ImagePathPieceSprites forRegistryKey(String registryKey) {
        if (registryKey == null) return null;
        String base = SyntheticCardNames.displayName(registryKey);
        for (ImagePathPieceSprites sprite : values()) {
            if (sprite.registryKey.equals(base)) return sprite;
        }
        return null;
    }
}
