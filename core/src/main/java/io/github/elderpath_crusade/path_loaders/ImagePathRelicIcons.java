package io.github.elderpath_crusade.path_loaders;

import lombok.Getter;

/**
 * Relic icons (ART-003), one per {@code relics.yaml} id — shown on {@code RelicHud}'s bubbles and
 * next to relic choices. A relic with no constant here falls back to its text/number label.
 */
@Getter
public enum ImagePathRelicIcons {
    PACK_LEADER("PackLeader"),
    BONFIRE("Bonfire"),
    HORN("Horn");

    private final String relicId;
    private final String path;

    ImagePathRelicIcons(String relicId) {
        this.relicId = relicId;
        this.path = "images/relics/" + relicId + ".png";
    }

    /** The icon for a relic id, or {@code null} if it has none. */
    public static ImagePathRelicIcons forRelicId(String relicId) {
        if (relicId == null) return null;
        for (ImagePathRelicIcons icon : values()) {
            if (icon.relicId.equals(relicId)) return icon;
        }
        return null;
    }
}
