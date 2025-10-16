package io.github.forest_of_dreams.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
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
        Label.LabelStyle cached = CACHED_STYLES.get(fontType);
        if (cached != null) return cached;

        String name = fontType.getFontName();
        // First try to load a custom BitmapFont from assets/fonts/<name>.fnt
        FileHandle fnt = Gdx.files.internal(FONTS_DIR + name + ".fnt");
        if (fnt.exists()) {
            BitmapFont bitmapFont = new BitmapFont(fnt);
            // If the bitmap font uses black RGB with alpha coverage (like Silkscreen),
            // make glyph RGB white so color tinting works correctly.
            if (fontType == FontType.SILKSCREEN) {
                makeFontRgbWhite(bitmapFont);
            }
            Label.LabelStyle style = new Label.LabelStyle();
            style.font = bitmapFont;
            CACHED_STYLES.put(fontType, style);
            return style;
        }

        // Fallback to skin style if custom font not found
        Label.LabelStyle style = SKIN.get(name, Label.LabelStyle.class);
        CACHED_STYLES.put(fontType, style);
        return style;
    }

    /**
     * Convert all font texture regions so that their RGB becomes white (255,255,255) while
     * preserving the original alpha channel. This fixes fonts generated with black RGB glyphs
     * where tinting would otherwise multiply by 0 and render black.
     */
    private static void makeFontRgbWhite(BitmapFont font) {
        for (TextureRegion region : font.getRegions()) {
            Texture oldTex = region.getTexture();
            int w = oldTex.getWidth();
            int h = oldTex.getHeight();

            TextureFilter min = oldTex.getMinFilter();
            TextureFilter mag = oldTex.getMagFilter();

            TextureData data = oldTex.getTextureData();
            if (!data.isPrepared()) data.prepare();
            Pixmap src = data.consumePixmap();
            // Create a copy we can safely modify without affecting the original reference
            Pixmap dst = new Pixmap(w, h, Pixmap.Format.RGBA8888);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int rgba = src.getPixel(x, y);
                    int a = (rgba & 0x000000ff); // Pixmap stores as RGBA8888 in int with channel order platform-dependent
                    // Extract channels in a format-agnostic way using Color? That would allocate. Instead,
                    // reinterpret assuming RGBA8888 as LibGDX Pixmap.getPixel returns RGBA8888 on desktop/most backends.
                    int r = (rgba >>> 24) & 0xff;
                    int g = (rgba >>> 16) & 0xff;
                    int b = (rgba >>> 8) & 0xff;
                    // Force RGB to 255 while preserving alpha from source
                    int out = (0xff << 24) | (0xff << 16) | (0xff << 8) | a;
                    dst.drawPixel(x, y, out);
                }
            }
            // If the pixmap is unmanaged by the texture, we should dispose it to avoid leaks
            if (!data.isManaged()) src.dispose();

            Texture newTex = new Texture(dst);
            newTex.setFilter(min, mag);
            // dst pixmap can be disposed after uploading to GPU
            dst.dispose();

            // Replace region's texture with the whitened one; coordinates remain valid as dimensions match
            region.setTexture(newTex);
        }
    }
}
