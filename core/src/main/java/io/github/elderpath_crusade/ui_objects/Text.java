package io.github.elderpath_crusade.ui_objects;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.settings.InputFunction;
import io.github.elderpath_crusade.interfaces.*;
import io.github.elderpath_crusade.managers.FontManager;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.managers.InputManager;
import io.github.elderpath_crusade.managers.SettingsManager;
import io.github.elderpath_crusade.supers.LowestOrderTexture;
import io.github.elderpath_crusade.utils.HoverUtils;
import io.github.elderpath_crusade.utils.FontSize;
import lombok.Getter;
import lombok.Setter;
import com.badlogic.gdx.utils.Align;

import java.util.HashMap;
import java.util.List;

public class Text extends LowestOrderTexture implements Renderable, UIRenderable, Clickable {
    @Getter @Setter private String text;
    @Getter @Setter private FontType fontType;
    private int z;

    private LabelStyle style;
    @Getter private Label label;

    private Color color;
    private Color hoverColor = null;
    private Color clickColor = null;

    private OnClick onClick = null;
    private ClickableEffectData clickableEffectData = null;
    @Setter private boolean pauseUIElement = false;

    // Font sizing
    private Float desiredFontSize = null; // desired cap-height in pixels; if null, uses scale
    private float fontScale = 1f; // relative scale fallback
    private Float maxFontSize = null; // maximum cap-height in pixels when wrapping; if null, no maximum
    // Wrapping support (X/Y)
    private boolean wrapEnabled = false;
    private int wrapWidth = 0;
    private int wrapHeight = 0;
    private boolean needsReflow = false;
    private int alignment = Align.center;

    public Text(String text, FontType fontType, int x, int y, int z, Color color) {
        this.text = text;
        this.fontType = fontType;
        this.z = z;
        this.color = color;

        setBounds(new Box(x, y, 0, 0));
        update();
    }

    public Text withHoverColor(Color hoverColor) {
        this.hoverColor = hoverColor;
        return this;
    }

    public Text withClickColor(Color clickColor) {
        this.clickColor = clickColor;
        return this;
    }

    public Text withOnClick(OnClick onClick, ClickableEffectData effectData) {
        setClickableEffect(onClick, effectData);
        return this;
    }

    /**
     * Convenience: mark as UI element.
     */
    public Text asPauseUI() {
        this.pauseUIElement = true;
        return this;
    }

    /**
     * Set the label's font size in pixels (approximately using cap-height).
     * This computes an internal scale relative to the BitmapFont's cap height.
     */
    public Text withFontSize(float pixels) {
        this.desiredFontSize = pixels;
        update();
        return this;
    }

    /**
     * Set the maximum font size in pixels (approximately using cap-height) when wrapping.
     * This caps the font size during the binary search algorithm to prevent text from being too large.
     *
     * @param maxPixels maximum cap-height in pixels, or null to remove the maximum
     */
    public Text withMaxFontSize(Float maxPixels) {
        this.maxFontSize = maxPixels;
        if (wrapEnabled) {
            this.needsReflow = true;
            update();
        }
        return this;
    }

    // --- Wrapping API ---
    public Text withWrapBounds(int width, int height) {
        this.wrapEnabled = true;
        this.wrapWidth = Math.max(0, width);
        this.wrapHeight = Math.max(0, height);
        this.needsReflow = true;
        update();
        return this;
    }

    public Text withWrapWidth(int width) {
        this.wrapEnabled = true;
        this.wrapWidth = Math.max(0, width);
        this.needsReflow = true;
        update();
        return this;
    }

    public Text clearWrap() {
        this.wrapEnabled = false;
        this.wrapWidth = 0;
        this.wrapHeight = 0;
        this.needsReflow = true;
        update();
        return this;
    }

    public Text withAlignment(int align) {
        this.alignment = align;
        update();
        return this;
    }

    public void reflow() {
        this.needsReflow = true;
        update();
    }

    // Convenience overload to use standardized enum
    public Text withFontSize(FontSize size) {
        return withFontSize(size.getSize());
    }

    public void update() {
        style = GameContext.get().getFontManager().getLabelStyle(fontType);
        label = new Label(text, style);
        label.setAlignment(alignment);
        label.setWrap(wrapEnabled);

        // Apply font sizing on the label (does not mutate the shared BitmapFont instance)
        if (!wrapEnabled) {
            // Legacy non-wrapped behavior
            if (desiredFontSize != null) {
                float baseCap = Math.abs(style.font.getCapHeight());
                if (baseCap > 0f) {
                    label.setFontScale(desiredFontSize / baseCap);
                } else {
                    label.setFontScale(fontScale);
                }
            } else {
                label.setFontScale(fontScale);
            }
            label.pack();
        } else {
            // Wrapped behavior: search for the largest scale that fits into wrapHeight (if provided)
            float baseCap = Math.abs(style.font.getCapHeight());
            float startScale = (desiredFontSize != null && baseCap > 0f)
                    ? Math.max(0.1f, desiredFontSize / baseCap)
                    : Math.max(0.1f, fontScale);

            // Calculate maximum scale from maxFontSize if set
            float maxScale = Float.MAX_VALUE;
            if (maxFontSize != null && baseCap > 0f) {
                maxScale = maxFontSize / baseCap;
            }

            // Cap startScale to maximum
            startScale = Math.min(startScale, maxScale);

            float lo = 0.01f; // definitely fits at very small scale
            float hi = Math.min(startScale, maxScale);

            // Initial check at high scale
            label.setFontScale(hi);
            label.setWidth(wrapWidth); // Must set width for wrapping to calculate height
            label.pack(); // Force layout recalc (but resets width to prefWidth which is 0 for wrap)
            // Actually, for wrapping labels, pack() sets width to 0. We must set it back.
            label.setWidth(wrapWidth);

            float targetH = (wrapHeight > 0 ? wrapHeight : Float.MAX_VALUE);

            // Check if hi fits immediately
            if (label.getPrefHeight() <= targetH) {
                // It fits, keep hi
                lo = hi;
            } else {
                // Binary search
                for (int i = 0; i < 14; i++) {
                    float mid = (lo + hi) * 0.5f;
                    label.setFontScale(mid);
                    label.setWidth(wrapWidth); // Ensure width is set for calc
                    // No need to pack, getPrefHeight uses current width/scale

                    if (label.getPrefHeight() <= targetH) {
                        lo = mid; // fits, try larger
                    } else {
                        hi = mid; // too big
                    }
                }
            }

            // Apply best scale (lo)
            label.setFontScale(lo);
            label.setWidth(wrapWidth);
            label.pack(); // Calculate final height
            label.setWidth(wrapWidth); // Restore width after pack
        }

        Box bounds = getBounds();
        label.setPosition(bounds.getX(), bounds.getY());
        label.setColor(color);

        // Bounds reflect label size (width capped to wrapWidth if wrapping)
        int w = (int) label.getWidth();
        int h = (int) label.getHeight();
        if (wrapEnabled && wrapWidth > 0) w = Math.min(wrapWidth, w);

        getBounds().setWidth(Math.max(0, w));
        getBounds().setHeight(Math.max(0, h));
        needsReflow = false;
    }

    public void setCenterX() {
        getBounds().setX((int) (GameContext.get().getSettingsManager().screenSize.getScreenCenter()[0] - (label.getWidth() / 2)));
    }

    public void setCenterY() {
        getBounds().setY((int) (GameContext.get().getSettingsManager().screenSize.getScreenCenter()[1] - (label.getHeight() / 2)));
    }

    private boolean isHovered(int relX, int relY) {
        int x = getX() + relX;
        int y = getY() + relY;
        return HoverUtils.isHovered(x, y, getWidth(), getHeight());
    }

    private boolean isClicked() {
        return GameContext.get().getInputManager().getFunctionActivation(InputFunction.LEFT_CLICK);
    }

    @Override
    public List<Integer> getZs() {
        return List.of(z);
    }

    @Override
    public boolean isPauseUIElement() {
        return pauseUIElement;
    }

    @Override
    public void setClickableEffect(OnClick onClick, ClickableEffectData effectData) {
        this.onClick = onClick;
        this.clickableEffectData = effectData;
    }

    @Override
    public ClickableEffectData getClickableEffectData() {
        return clickableEffectData;
    }

    @Override
    public void triggerClickEffect(HashMap<Integer, CustomBox> interactionEntities) {
        if (this.onClick == null) return;
        this.onClick.run(interactionEntities);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        if (zLevel != z) return;
        if (isPaused) return;
        // Keep label in sync with bounds without reallocating style/label on every frame
        if (label != null) {
            label.setPosition(getBounds().getX(), getBounds().getY());
        }
        if (isHovered(0, 0)) {
            if (hoverColor != null) label.setColor(hoverColor);
            if (isClicked() && clickColor != null) label.setColor(clickColor);
        }
        label.draw(batch, 1);
        label.setColor(color);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        if (zLevel != z) return;
        if (isPaused) return;
        label.setPosition(x, y);
        if (isHovered(x, y)) {
            if (hoverColor != null) label.setColor(hoverColor);
            if (isClicked() && clickColor != null) label.setColor(clickColor);
        }
        label.draw(batch, 1);
        label.setColor(color);
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused) {
        render(batch, z, isPaused);
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused, int x, int y) {
        render(batch, z, isPaused, x, y);
    }

//    public void onClick() {
//        if (onClick != null) onClick.run();
//    }
}
