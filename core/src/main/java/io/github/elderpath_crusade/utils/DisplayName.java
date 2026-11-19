package io.github.elderpath_crusade.utils;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.GameContext;

import java.util.function.IntPredicate;

/**
 * Makes a player's name safe to draw. Names will come from outside (a Steam profile name can hold emoji, CJK,
 * right-to-left marks, control characters and 32+ characters), and the game's pixel font covers only a small
 * alphabet: anything it cannot draw becomes '?', invisible/format characters are dropped, runs of whitespace
 * collapse, and a long name is cut with ".." so it never runs off the screen.
 */
public final class DisplayName {
    private DisplayName() {}

    /** Pure form: hasGlyph says whether the font can draw a character. Returns "" for null/blank input. */
    public static String forDisplay(String name, int maxChars, IntPredicate hasGlyph) {
        if (name == null || maxChars < 3) return "";
        StringBuilder cleaned = new StringBuilder();
        boolean pendingSpace = false;
        for (int i = 0; i < name.length(); ) {
            int cp = name.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                pendingSpace = cleaned.length() > 0;
                continue;
            }
            int type = Character.getType(cp);
            if (type == Character.CONTROL || type == Character.FORMAT || type == Character.UNASSIGNED
                    || type == Character.SURROGATE || type == Character.PRIVATE_USE) {
                continue;
            }
            if (pendingSpace) {
                cleaned.append(' ');
                pendingSpace = false;
            }
            cleaned.append(cp <= Character.MAX_VALUE && hasGlyph.test(cp) ? new String(Character.toChars(cp)) : "?");
        }
        if (cleaned.length() <= maxChars) return cleaned.toString();
        return cleaned.substring(0, maxChars - 2) + "..";
    }

    /** With the game's own font: what the Silkscreen labels can actually draw. */
    public static String forScreen(String name, int maxChars) {
        BitmapFont.BitmapFontData data = GameContext.get().getFontManager().getLabelStyle(FontType.SILKSCREEN).font.getData();
        return forDisplay(name, maxChars, cp -> data.hasGlyph((char) cp));
    }
}
