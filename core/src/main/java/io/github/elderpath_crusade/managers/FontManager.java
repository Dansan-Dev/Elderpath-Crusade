package io.github.elderpath_crusade.managers;

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
import io.github.elderpath_crusade.enums.FontType;

import java.util.EnumMap;
import java.util.Map;

public class FontManager {
    private static final String FONTS_DIR = "fonts/";
    private Skin skin;
    private final Map<FontType, Label.LabelStyle> cachedStyles = new EnumMap<>(FontType.class);

    public FontManager() {}

    private Skin getSkin() {
        if (skin == null) {
            skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
        }
        return skin;
    }

    public Label.LabelStyle getLabelStyle(FontType fontType) {
        if (fontType == null) fontType = FontType.DEFAULT;
        Label.LabelStyle cached = cachedStyles.get(fontType);
        if (cached != null) return cached;

        String name = fontType.getFontName();
        FileHandle fnt = Gdx.files.internal(FONTS_DIR + name + ".fnt");
        if (fnt.exists()) {
            BitmapFont bitmapFont = new BitmapFont(fnt);
            if (fontType == FontType.SILKSCREEN) {
                makeFontRgbWhite(bitmapFont);
            }
            Label.LabelStyle style = new Label.LabelStyle();
            style.font = bitmapFont;
            cachedStyles.put(fontType, style);
            return style;
        }

        Label.LabelStyle style = getSkin().get(name, Label.LabelStyle.class);
        cachedStyles.put(fontType, style);
        return style;
    }

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
            Pixmap dst = new Pixmap(w, h, Pixmap.Format.RGBA8888);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int rgba = src.getPixel(x, y);
                    int a = (rgba & 0x000000ff);
                    int out = (0xff << 24) | (0xff << 16) | (0xff << 8) | a;
                    dst.drawPixel(x, y, out);
                }
            }
            if (!data.isManaged()) src.dispose();

            Texture newTex = new Texture(dst);
            newTex.setFilter(min, mag);
            dst.dispose();
            region.setTexture(newTex);
        }
    }
}
