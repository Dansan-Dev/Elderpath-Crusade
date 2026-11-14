package io.github.elderpath_crusade.path_loaders;

import io.github.elderpath_crusade.match.SyntheticCardNames;
import lombok.Getter;

/**
 * Spell card portraits (ART-004), one per {@code spells.yaml} id (spaces dropped, e.g.
 * "Healing Light" → {@code HealingLight}), drawn in the card front's arch
 * window. Summon cards use their piece's {@link ImagePathPieceSprites} figure instead.
 */
@Getter
public enum ImagePathSpellArt {
    FIREBALL("Fireball"),
    FROSTBOLT("Frostbolt"),
    HEALING_LIGHT("HealingLight"),
    SCAVENGE("Scavenge"),
    CHAIN_LIGHTNING("ChainLightning"),
    BLIZZARD("Blizzard");

    private final String spellId;
    private final String path;

    ImagePathSpellArt(String spellId) {
        this.spellId = spellId;
        this.path = "images/spells/" + spellId + ".png";
    }

    /** The art for a spell's id or card name (spaces ignored, run suffix stripped), or {@code null}. */
    public static ImagePathSpellArt forSpell(String spellName) {
        if (spellName == null) return null;
        String key = SyntheticCardNames.displayName(spellName).replace(" ", "");
        for (ImagePathSpellArt art : values()) {
            if (art.spellId.equals(key)) return art;
        }
        return null;
    }
}
