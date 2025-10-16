package io.github.forest_of_dreams.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import io.github.forest_of_dreams.enums.FontType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Lightweight font manager that prefers custom BitmapFont files from assets/fonts,
 * falling back to the default UI skin styles when a custom font is not found.
 */
public final class FontManager {
    private static final String FONTS_DIR = "fonts/"; // relative to assets/
    private static final Skin SKIN = new Skin(Gdx.files.internal("ui/uiskin.json"));

    private static final Map<FontType, Label.LabelStyle> CACHED_STYLES = new EnumMap<>(FontType.class);

    private FontManager() {}

    public static Label.LabelStyle getLabelStyle(FontType fontType) {
        if (fontType == null) fontType = FontType.DEFAULT;
        Label.LabelStyle style = CACHED_STYLES.get(fontType);
        if (style != null) return style;

        String name = fontType.getFontName();
        // First try to load a custom BitmapFont from assets/fonts/<name>.fnt
        FileHandle fnt = Gdx.files.internal(FONTS_DIR + name + ".fnt");
        if (fnt.exists()) {
            BitmapFont bitmapFont = new BitmapFont(fnt);
            style = new Label.LabelStyle();
            style.font = bitmapFont;
            CACHED_STYLES.put(fontType, style);
            return style;
        }

        // Fallback to skin style if custom font not found
        style = SKIN.get(name, Label.LabelStyle.class);
        CACHED_STYLES.put(fontType, style);
        return style;
    }
}
