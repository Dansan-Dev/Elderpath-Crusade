package io.github.forest_of_dreams.data_objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.utils.ColorSettings;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.interfaces.*;
import io.github.forest_of_dreams.supers.AbstractTexture;
import io.github.forest_of_dreams.utils.GraphicUtils;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;

/**
 * A simple Button component that renders a rectangular box (image or solid color),
 * centers text inside the box, and triggers an onClick when clicked.
 */
public class Button extends AbstractTexture implements Renderable, Clickable, UIRenderable {
    @Getter @Setter private String text;
    @Getter @Setter private FontType fontType;
    @Getter @Setter private Color textColor = ColorSettings.TEXT_DEFAULT.getColor();
    @Getter @Setter private int z;

    // Background options (only one should be used)
    private Texture backgroundTexture; // full texture scaled to bounds
    private Color backgroundColor;     // solid color fill

    // Optional hover / click visual tweaks (background)
    @Getter @Setter private Color hoverColor = null;      // background hover color
    @Getter @Setter private Color clickColor = null;      // background click color

    // Optional hover / click visual tweaks (text)
    @Getter @Setter private Color hoverTextColor = null;
    @Getter @Setter private Color clickTextColor = null;

    // Optional border colors
    @Getter @Setter private Color borderColor = null;
    @Getter @Setter private Color hoverBorderColor = null;
    @Getter @Setter private Color clickBorderColor = null;

    private Text textObj;

    // Clickable integration
    private OnClick onClick = null;
    private ClickableEffectData clickableEffectData = null;

    private Button(String text, FontType fontType, int fontSize, int x, int y, int width, int height, int z) {
        this.text = text;
        this.fontType = fontType;
        this.z = z;
        this.textObj = new Text(text, fontType, x, y, z, textColor);
        setBounds(new Box(x, y, width, height));
        updateText();
        textObj.withFontSize(fontSize);
    }

    // Factory: Color background
    public static Button fromColor(
        Color backgroundColor,
        String text,
        FontType fontType,
        int fontSize,
        int x, int y,
        int width, int height,
        int z
    ) {
        Button b = new Button(text, fontType, fontSize, x, y, width, height, z);
        b.backgroundColor = backgroundColor;
        return b;
    }

    // Factory: Image background (path to image)
    public static Button fromImage(
        String imagePath,
        String text,
        FontType fontType,
        int fontSize,
        int x, int y,
        int width, int height,
        int z
    ) {
        Button b = new Button(text, fontType, fontSize, x, y, width, height, z);
        b.backgroundTexture = new Texture(Gdx.files.internal(imagePath));
        return b;
    }

    public Button withOnClick(OnClick onClick, ClickableEffectData effectData) {
        setClickableEffect(onClick, effectData);
        return this;
    }

    public Button withTextColors(Color normal, Color hover, Color click) {
        this.textColor = normal;
        this.hoverTextColor = hover;
        this.clickTextColor = click;
        if (textObj != null && textObj.getLabel() != null) textObj.getLabel().setColor(textColor);
        return this;
    }

    private void updateText() {
        // Update to ensure label, size and style are current
        textObj.update();
        centerLabelInBounds();
    }

    private void centerLabelInBounds() {
        Box b = getBounds();
        if (b == null || textObj == null || textObj.getLabel() == null) return;
        // Ensure label has latest pref size
        Label lbl = textObj.getLabel();
        float labelWidth = lbl.getPrefWidth();
        float labelHeight = lbl.getPrefHeight();
        int baseX = getX(); // absolute x
        int baseY = getY(); // absolute y
        Box newBounds = new Box(
            (int) (baseX + (b.getWidth() - labelWidth) / 2f),
            (int) (baseY + (b.getHeight() - labelHeight) / 2f),
            (int) labelWidth,
            (int) labelHeight
        );
        textObj.setBounds(newBounds);
    }

    private boolean isHovered(int relX, int relY) {
        int x = getX() + relX;
        int y = getY() + relY;
        int width = getWidth();
        int height = getHeight();

        // Scale the mouse coordinates
        float mouseX = Gdx.input.getX();
        float mouseY = Gdx.graphics.getHeight() - Gdx.input.getY();

        boolean isInXRange = x <= mouseX && mouseX <= (x + width - 1);
        boolean isInYRange = y <= mouseY && mouseY <= (y + height - 1);

        return isInXRange && isInYRange;
    }

    @Override
    public List<Integer> getZs() {
        return List.of(z);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        if (isPaused) return;
        if (zLevel != z) return;
        drawBackground(batch);
        updateText();
        applyHoverClickTint(0, 0);
        if (textObj != null && textObj.getLabel() != null) {
            textObj.getLabel().draw(batch, 1);
            textObj.getLabel().setColor(textColor);
        }
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        if (isPaused) return;
        if (zLevel != z) return;
        // Temporarily offset bounds for drawing in higher-order containers
        Box original = getBounds();
        Box temp = new Box(x, y, original.getWidth(), original.getHeight());
        setBounds(temp);

        drawBackground(batch);
        updateText();
        applyHoverClickTint(x, y);
        if (textObj != null && textObj.getLabel() != null) {
            textObj.getLabel().draw(batch, 1);
            textObj.getLabel().setColor(textColor);
        }

        setBounds(original);
    }

    private void drawBackground(SpriteBatch batch) {
        Box bounds = getBounds();
        int xAbs = getX();
        int yAbs = getY();
        int width = bounds.getWidth();
        int height = bounds.getHeight();

        if (backgroundTexture != null) {
            batch.draw(backgroundTexture, xAbs, yAbs, width, height);
        } else if (backgroundColor != null) {
            // Determine background color based on hover/click state (click overrides hover)
            Color bg = backgroundColor;
            boolean hovered = isHovered(0, 0);
            if (hovered && hoverColor != null) bg = hoverColor;
            if (hovered && Gdx.input.isTouched() && clickColor != null) bg = clickColor;
            Texture pixel = GraphicUtils.getPixelTexture(bg);
            batch.draw(pixel, xAbs, yAbs, width, height);
        }

        // Optional border (drawn regardless of background type)
        Color activeBorder = null;
        if (borderColor != null) {
            activeBorder = borderColor;
            boolean hovered = isHovered(0, 0);
            if (hovered && hoverBorderColor != null) activeBorder = hoverBorderColor;
            if (hovered && Gdx.input.isTouched() && clickBorderColor != null) activeBorder = clickBorderColor;
        }
        if (activeBorder != null) {
            Texture px = GraphicUtils.getPixelTexture(activeBorder);
            int thickness = 1; // border thickness in pixels
            if (width > 0 && height > 0) {
                // Top
                batch.draw(px, xAbs, yAbs + height - thickness, width, thickness);
                // Bottom
                batch.draw(px, xAbs, yAbs, width, thickness);
                // Left
                batch.draw(px, xAbs, yAbs, thickness, height);
                // Right
                batch.draw(px, xAbs + width - thickness, yAbs, thickness, height);
            }
        }

        // else: transparent background (only text)
        centerLabelInBounds();
    }

    private void applyHoverClickTint(int relX, int relY) {
        if (!isHovered(relX, relY)) return;
        if (textObj == null || textObj.getLabel() == null) return;
        if (hoverTextColor != null) textObj.getLabel().setColor(hoverTextColor);
        // Visual click color change if provided
        if (clickTextColor != null && Gdx.input.isTouched()) textObj.getLabel().setColor(clickTextColor);
    }

    // Clickable implementation hooks
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

    // Background hover color setter (hoverColor)
    public Button withHoverColor(Color hoverColor) {
        this.hoverColor = hoverColor;
        return this;
    }

    // Text hover color setter (hoverTextColor)
    public Button withHoverTextColor(Color hoverTextColor) {
        this.hoverTextColor = hoverTextColor;
        return this;
    }

    // Background click color setter (clickColor)
    public Button withClickColor(Color clickColor) {
        this.clickColor = clickColor;
        return this;
    }

    // Text click color setter (clickTextColor)
    public Button withClickTextColor(Color clickTextColor) {
        this.clickTextColor = clickTextColor;
        return this;
    }

    // Border base color setter
    public Button withBorderColor(Color borderColor) {
        this.borderColor = borderColor;
        return this;
    }

    // Border hover color setter
    public Button withHoverBorderColor(Color hoverBorderColor) {
        this.hoverBorderColor = hoverBorderColor;
        return this;
    }

    // Border click color setter
    public Button withClickBorderColor(Color clickBorderColor) {
        this.clickBorderColor = clickBorderColor;
        return this;
    }

    // UIRenderable implementation
    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused) {
        this.render(batch, this.z, isPaused);
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused, int x, int y) {
        // Ignore external offset; use parent-relative absolute via calculatePos()
        this.renderUI(batch, isPaused);
    }

    // Ensure absolute positioning for interactions when nested in UI containers
    @Override
    public int getX() {
        int[] pos = calculatePos();
        return pos[0];
    }

    @Override
    public int getY() {
        int[] pos = calculatePos();
        return pos[1];
    }
}
