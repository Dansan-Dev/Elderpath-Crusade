package io.github.elderpath_crusade.path_loaders;

import lombok.Getter;

/**
 * Small standalone icons (UI-003 and the mana HUD): keyword badges drawn on unit cards in place
 * of their rules text, the Elder badge beside a card's cost, and the mana icon. Each file is a
 * tight square around its art (no fixed size) — consumers scale it to the rect they draw.
 * Optional at runtime like every other ART-00x image: a missing file means the consumer falls
 * back to plain text.
 */
@Getter
public enum ImagePathIcons {
    KEYWORD_RANGE("keyword_range.png"),
    KEYWORD_CANNOT_ATTACK("keyword_cannot_attack.png"),
    ELDER("elder.png"),
    MANA("mana.png");

    private final String path;

    ImagePathIcons(String file) {
        this.path = "images/icons/" + file;
    }
}
