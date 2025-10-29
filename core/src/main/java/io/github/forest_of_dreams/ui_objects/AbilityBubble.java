package io.github.forest_of_dreams.ui_objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.data_objects.ClickableEffectData;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.interfaces.Clickable;
import io.github.forest_of_dreams.interfaces.CustomBox;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.interfaces.UIRenderable;
import io.github.forest_of_dreams.supers.LowestOrderTexture;
import io.github.forest_of_dreams.ui_objects.Text;
import io.github.forest_of_dreams.utils.ColorSettings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal, purpose-built bubble for ability UI. Supports optional icon (aspect preserved)
 * or a small numeric label fallback. Keeps Button class lean by moving bubble-specific
 * behavior here. Rendered as a circle smaller than a plot.
 */
public class AbilityBubble extends LowestOrderTexture implements Renderable, UIRenderable, Clickable {
    private final int z;
    private Texture iconTexture; // optional
    private final int size; // square bubble size in pixels
    private final Color bgColor;
    private final Color borderColor;
    private final int borderThickness;
    private final boolean shadow = true;

    // Optional numeric label fallback
    private Text labelText; // centered small number, e.g., "1", "2" (FontType.SILKSCREEN)

    // Clickable plumbing
    private ClickableEffectData effectData;
    private io.github.forest_of_dreams.interfaces.OnClick onClick;

    // Cache for generated circle textures per key (size+colors)
    private static final Map<String, Texture> CIRCLE_CACHE = new ConcurrentHashMap<>();
    // App-lifetime cache for ability icon textures by internal path
    private static final Map<String, Texture> ICON_CACHE = new ConcurrentHashMap<>();

    public AbilityBubble(int x, int y, int size, int z) {
        this(x, y, size, z, Color.WHITE.cpy().mul(0.15f, 0.15f, 0.35f, 0.95f), Color.WHITE.cpy().mul(0.8f));
    }

    public AbilityBubble(int x, int y, int size, int z, Color bgColor, Color borderColor) {
        this.z = z;
        this.size = Math.max(1, size);
        this.bgColor = (bgColor == null ? Color.WHITE.cpy().mul(0.15f,0.15f,0.35f,0.95f) : bgColor);
        this.borderColor = (borderColor == null ? Color.WHITE.cpy().mul(0.8f) : borderColor);
        this.borderThickness = Math.max(1, Math.round(this.size * 0.08f));
        setBounds(new Box(x, y, this.size, this.size));
    }

    public AbilityBubble withIcon(String internalPath) {
        if (internalPath != null && !internalPath.isBlank()) {
            try {
                Texture cached = ICON_CACHE.get(internalPath);
                if (cached == null) {
                    cached = new Texture(Gdx.files.internal(internalPath));
                    ICON_CACHE.put(internalPath, cached);
                }
                this.iconTexture = cached;
            } catch (Exception ignored) {
                this.iconTexture = null;
            }
        }
        return this;
    }

    public AbilityBubble withIndexLabel(int index, Color color) {
        String text = String.valueOf(Math.max(1, index));
        this.labelText = new Text(text, FontType.SILKSCREEN, 0, 0, z, (color == null ? ColorSettings.TEXT_DEFAULT.getColor() : color));
        // Reasonable font size for the circle; will be re-centered on render
        this.labelText.withFontSize(Math.max(10, Math.round(size * 0.5f)));
        return this;
    }

    public AbilityBubble withOnClick(io.github.forest_of_dreams.interfaces.OnClick onClick, ClickableEffectData data) {
        this.onClick = onClick;
        this.effectData = data;
        return this;
    }

    @Override
    public List<Integer> getZs() { return List.of(z); }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        if (isPaused) return;
        if (zLevel != z) return;
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        // Draw cached circle texture (with optional subtle shadow and border)
        Texture circle = getCircleTexture(w, h, bgColor, borderColor, borderThickness, shadow);
        batch.draw(circle, x, y, w, h);
        // icon or label centered
        if (iconTexture != null) {
            int texW = iconTexture.getWidth();
            int texH = iconTexture.getHeight();
            if (texW > 0 && texH > 0) {
                int pad = Math.max(1, Math.round(Math.min(w, h) * 0.18f)); // keep icon away from border
                float scale = Math.min((float) (w - pad * 2) / texW, (float) (h - pad * 2) / texH);
                int drawW = Math.max(1, Math.round(texW * scale));
                int drawH = Math.max(1, Math.round(texH * scale));
                int dx = x + (w - drawW) / 2;
                int dy = y + (h - drawH) / 2;
                batch.draw(iconTexture, dx, dy, drawW, drawH);
            }
        } else if (labelText != null) {
            int tx = x + (w - labelText.getWidth()) / 2;
            int ty = y + (h - labelText.getHeight()) / 2;
            labelText.render(batch, zLevel, false, tx, ty);
        }
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        // ignore external offsets, this is positioned absolutely via bounds
        render(batch, zLevel, isPaused);
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused) {
        render(batch, z, isPaused);
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused, int x, int y) {
        render(batch, z, isPaused);
    }

    @Override
    public ClickableEffectData getClickableEffectData() {
        return effectData;
    }

    @Override
    public void triggerClickEffect(HashMap<Integer, CustomBox> interactionEntities) {
        if (onClick != null) onClick.run(interactionEntities);
    }

    private static Texture getCircleTexture(int w, int h, Color fill, Color border, int borderThickness, boolean withShadow) {
        int size = Math.min(w, h);
        String key = size + ":" + colorKey(fill) + ":" + colorKey(border) + ":" + borderThickness + ":" + (withShadow ? 1 : 0);
        Texture cached = CIRCLE_CACHE.get(key);
        if (cached != null) return cached;
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setColor(0,0,0,0);
        pm.fill();
        int radius = size / 2;
        int cx = radius;
        int cy = radius;
        // Shadow (simple radial soft edge): draw a slightly larger, semi-transparent circle behind
        if (withShadow) {
            pm.setColor(0f, 0f, 0f, 0.25f);
            int rShadow = Math.max(1, radius - 1);
            // offset shadow by 1px down to suggest depth
            pm.fillCircle(cx, cy - 1, rShadow);
        }
        // Fill
        pm.setColor(fill);
        pm.fillCircle(cx, cy, Math.max(1, radius - borderThickness));
        // Border ring
        if (borderThickness > 0) {
            pm.setColor(border);
            // draw multiple rings to approximate thickness
            for (int t = 0; t < borderThickness; t++) {
                pm.drawCircle(cx, cy, Math.max(1, radius - t));
            }
        }
        Texture tex = new Texture(pm);
        pm.dispose();
        CIRCLE_CACHE.put(key, tex);
        return tex;
    }

    private static String colorKey(Color c) {
        if (c == null) return "null";
        return (int)(c.r*255) +","+ (int)(c.g*255) +","+ (int)(c.b*255) +","+ (int)(c.a*255);
    }
}
