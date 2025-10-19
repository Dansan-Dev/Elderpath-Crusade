package io.github.forest_of_dreams.data_objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.enums.settings.InputFunction;
import io.github.forest_of_dreams.interfaces.*;
import io.github.forest_of_dreams.managers.FontManager;
import io.github.forest_of_dreams.managers.InputManager;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.supers.AbstractTexture;
import io.github.forest_of_dreams.utils.HoverUtils;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;

public class Text extends AbstractTexture implements Renderable, UIRenderable, Clickable {
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

    // Convenience overload to use standardized enum
    public Text withFontSize(io.github.forest_of_dreams.utils.FontSize size) {
        return withFontSize(size.getSize());
    }

    public void update() {
        style = FontManager.getLabelStyle(fontType);
        label = new Label(text, style);

        // Apply font sizing on the label (does not mutate the shared BitmapFont instance)
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

        Box bounds = getBounds();
        label.setPosition(bounds.getX(), bounds.getY());
        label.setColor(color);

        // Ensure preferred size is computed with current scale
        label.pack();
        getBounds().setWidth((int) label.getWidth());
        getBounds().setHeight((int) label.getHeight());
    }



    public void setCenterX() {
        getBounds().setX((int)(SettingsManager.screenSize.getScreenCenter()[0] - (label.getWidth() / 2)));
    }

    public void setCenterY() {
        getBounds().setY((int)(SettingsManager.screenSize.getScreenCenter()[1] - (label.getHeight() / 2)));
    }

    private boolean isHovered(int relX, int relY) {
        int x = getX() + relX;
        int y = getY() + relY;
        return HoverUtils.isHovered(x, y, getWidth(), getHeight());
    }

    private boolean isClicked() {
        return InputManager.getFunctionActivation(InputFunction.LEFT_CLICK);
    }

    @Override
    public List<Integer> getZs() {
        return List.of();
    }

    @Override
    public boolean isPauseUIElement() { return pauseUIElement; }

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
            if (isClicked() && clickColor != null) {
                label.setColor(clickColor);
            }
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
